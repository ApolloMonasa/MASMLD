package dev.masmld.core.binary

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.openapi.actionSystem.*
import java.io.File

object BinaryOutput {
    fun symbols(text: String): Map<String, String> = text.lineSequence().take(200_000).mapNotNull { line ->
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.size >= 3 && fields[1].matches(Regex("[A-TV-Za-tv-z]")) && fields[2].matches(Regex("[0-9a-fA-F]+"))) fields[0] to "0x${fields[2]}" else null
    }.toMap()
    data class Location(val path: String, val line: Int, val column: Int)
    fun location(text: String): Location? = text.lineSequence().mapNotNull { line ->
        Regex("^(.*?):([0-9]+)(?::([0-9]+))?(?: \\(.*\\))?$").matchEntire(line.trim())?.let {
            val path = it.groupValues[1]; val number = it.groupValues[2].toIntOrNull() ?: return@let null
            if (path == "??" || number < 1) null else Location(path, number - 1, (it.groupValues[3].toIntOrNull() ?: 1).coerceAtLeast(1) - 1)
        }
    }.firstOrNull()
}
class BinarySymbols(private val project: Project) {
    private data class Snapshot(val symbols: Map<String, String>, val fingerprint: String)
    @Volatile private var snapshot = Snapshot(emptyMap(), "")
    private fun artifact(): File { val path = BinarySettings.get(project).state.artifact; return File(path).let { if (it.isAbsolute) it else File(project.basePath, path) } }
    private fun stamp() = artifact().let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" }
    fun clear() { snapshot = Snapshot(emptyMap(), "") }
    fun refresh(): Int {
        val file = artifact(); require(file.isFile) { "Configure an existing linked artifact in MASMLD Binary Symbols settings" }
        val before = stamp()
        val output = run(listOf(BinarySettings.get(project).state.nm, "--defined-only", "--format=posix", file.absolutePath), 30000)
        val loaded = BinaryOutput.symbols(output)
        require(before == stamp()) { "Artifact changed while loading symbols; refresh after the build finishes" }
        snapshot = Snapshot(loaded, before)
        return loaded.size
    }
    fun target(origin: PsiElement, name: String): PsiElement? {
        val current = snapshot
        val address = current.symbols[name] ?: return null
        if (current.fingerprint != stamp()) return null
        val file = artifact()
        val version = current.fingerprint
        return object : FakePsiElement() {
            override fun getParent() = origin
            override fun getName() = name
            override fun getPresentableText() = "$name — $address"
            override fun getLocationString() = file.name
            override fun getContainingFile(): PsiFile? = null
            override fun isValid() = origin.isValid && snapshot === current && version == stamp()
            override fun canNavigate() = isValid
            override fun canNavigateToSource() = isValid
            override fun navigate(requestFocus: Boolean) {
                object : Task.Backgroundable(project, "Resolve linked symbol $name", true) {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            require(snapshot === current && version == stamp()) { "Artifact changed; refresh binary symbols" }
                            val tool = BinarySettings.get(project).state.symbolizer
                            val args = if (File(tool).name.startsWith("addr2line")) listOf(tool, "-e", file.absolutePath, "-f", "-C", address)
                                else listOf(tool, "--obj=${file.absolutePath}", "--inlining=false", address)
                            val location = BinaryOutput.location(run(args, 5000)) ?: error("No source location; build the artifact with debug information")
                            require(snapshot === current && version == stamp()) { "Artifact changed during symbolization; refresh binary symbols" }
                            ApplicationManager.getApplication().invokeLater {
                                if (!project.isDisposed) {
                                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(File(location.path).let { if (it.isAbsolute) it else File(project.basePath, location.path) }.absolutePath.replace('\\', '/'))
                                    if (vf != null) OpenFileDescriptor(project, vf, location.line, location.column).navigate(requestFocus)
                                    else Messages.showInfoMessage(project, "Source file is unavailable: ${location.path}", "MASMLD Binary Symbols")
                                }
                            }
                        } catch (cancel: com.intellij.openapi.progress.ProcessCanceledException) { throw cancel }
                        catch (failure: Exception) { showFailure(project, failure) }
                    }
                }.queue()
            }
        }
    }
    companion object {
        fun get(project: Project) = project.getService(BinarySymbols::class.java)
        fun run(args: List<String>, timeout: Int): String {
            ProgressManager.checkCanceled()
            val output = CapturingProcessHandler(GeneralCommandLine(args)).runProcess(timeout, true)
            require(!output.isTimeout && output.exitCode == 0) { "Tool failed or timed out: ${File(args.first()).name}. Check executable paths and artifact format." }
            ProgressManager.checkCanceled()
            return output.stdout
        }
        fun showFailure(project: Project, failure: Exception) { ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) Messages.showInfoMessage(project, failure.message ?: "Tool failed", "MASMLD Binary Symbols")
        } }
    }
}
class RefreshBinarySymbols : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        object : Task.Backgroundable(project, "Load linked binary symbols", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val count = BinarySymbols.get(project).refresh()
                    ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) Messages.showInfoMessage(project, "Loaded $count symbols", "MASMLD Binary Symbols") }
                } catch (cancel: com.intellij.openapi.progress.ProcessCanceledException) { throw cancel }
                catch (failure: Exception) { BinarySymbols.showFailure(project, failure) }
            }
        }.queue()
    }
}
