package dev.masmld.riscv.symbols

import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import dev.masmld.core.language.AssemblyFileType

/** Text-only index; never depends on project settings or reads another file during indexing. */
class RiscVExportIndex : FileBasedIndexExtension<String, String>() {
    override fun getName() = ID
    override fun getVersion() = 2
    override fun dependsOnFileContent() = true
    override fun getInputFilter() = FileBasedIndex.InputFilter { file -> file.fileType == AssemblyFileType || file.extension?.lowercase() == "inc" }
    override fun getKeyDescriptor() = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer() = EnumeratorStringDescriptor.INSTANCE
    override fun getIndexer() = DataIndexer<String, String, FileContent> { content ->
        RiscVSymbols.analyze(content.contentAsText).definitions.filter { it.exported }.associate { it.name to it.start.toString() }
    }
    companion object { val ID: ID<String, String> = com.intellij.util.indexing.ID.create("dev.masmld.riscv.exports") }
}
