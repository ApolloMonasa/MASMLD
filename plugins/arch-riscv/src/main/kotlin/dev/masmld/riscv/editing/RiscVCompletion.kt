package dev.masmld.riscv.editing

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import dev.masmld.core.documentation.DocumentationKind as K
import dev.masmld.core.settings.AssemblySettings
import dev.masmld.core.source.SourceText
import dev.masmld.core.source.SourceToken
import dev.masmld.riscv.documentation.RiscVDocumentationCatalog
import dev.masmld.riscv.symbols.RiscVProgram

/** Context calculation also works on the original document before the IDE inserts its dummy identifier. */
object RiscVCompletionContext {
    fun activeMacros(source: String, offset: Int): Map<String, Int> {
        val active = linkedMapOf<String, Int>(); var depth = 0
        val tokens = SourceText.tokens(source.take(offset.coerceIn(0, source.length)))
        for ((i, token) in tokens.withIndex()) {
            if (token.text == ".macro") { if (depth == 0) tokens.getOrNull(i + 1)?.let { active[it.text] = it.start }; depth++ }
            if (token.text == ".endm") depth = (depth - 1).coerceAtLeast(0)
            if (depth == 0 && token.text == ".purgem") tokens.getOrNull(i + 1)?.let { active.remove(it.text) }
        }
        return active
    }
    fun head(source: String, offset: Int): Boolean? {
        val token = SourceText.tokens(source, true).firstOrNull { offset > it.start && (offset < it.end || offset == it.end && it.kind == SourceToken.Kind.COMMENT && !it.text.endsWith("*/")) }
        if (token?.kind in setOf(SourceToken.Kind.STRING, SourceToken.Kind.COMMENT)) return null
        // Unlike the scanner, this prefix retains comments so completion cannot escape a line comment.
        val prefix = source.substring(0, offset.coerceIn(0, source.length))
        val line = prefix.substringAfterLast('\n').substringAfterLast(';')
        if (line.contains('#') || line.contains("//")) return null
        val code = line.replace(Regex("^\\s*(?:[\\w.$]+:\\s*)+"), "")
        return code.trimStart().none(Char::isWhitespace)
    }
}
class RiscVCompletion : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val source = file.text
        val offset = parameters.offset.coerceAtMost(source.length)
        val head = RiscVCompletionContext.head(source, offset) ?: return
        val prefix = source.take(offset).takeLastWhile { it.isLetterOrDigit() || it in "_.\$" }
        // The lookup prefix determines how much typed text IntelliJ replaces on selection.
        val completions = if (head && prefix.startsWith('.')) result.withPrefixMatcher(prefix) else result
        val program = RiscVProgram.get(file)
        val isa = program.targetAt(file, offset)
        RiscVDocumentationCatalog.entries.filter { entry ->
            (if (head) entry.kind in setOf(K.INSTRUCTION, K.PSEUDO_INSTRUCTION, K.DIRECTIVE) else entry.kind == K.REGISTER) && RiscVIsa.supports(entry.name, isa)
        }.forEach { entry -> completions.addElement(LookupElementBuilder.create(entry.name).withTypeText(entry.kind.name.lowercase()).withTailText("  ${entry.syntax}", true)) }
        if (!head && !com.intellij.openapi.project.DumbService.isDumb(file.project)) {
            val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(file.project)
            var count = 0
            try {
                com.intellij.util.indexing.FileBasedIndex.getInstance().processAllKeys(dev.masmld.riscv.symbols.RiscVExportIndex.ID, com.intellij.util.Processor<String> { name ->
                    com.intellij.openapi.progress.ProgressManager.checkCanceled()
                    if (completions.prefixMatcher.prefixMatches(name) && com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(dev.masmld.riscv.symbols.RiscVExportIndex.ID, name, scope).any { candidate ->
                            AssemblySettings.getInstance(file.project).selection(candidate.path.removePrefix(file.project.basePath.orEmpty() + "/")).architecture == "riscv"
                        }) {
                        completions.addElement(LookupElementBuilder.create(name).withTypeText("project export")); count++
                    }
                    count < 200
                }, scope, null)
            } catch (_: com.intellij.openapi.project.IndexNotReadyException) { /* Local suggestions remain available. */ }
        }
        val expandedOffset = program.expanded.expandedOffset(file.virtualFile?.url.orEmpty(), offset - 1) ?: Int.MAX_VALUE
        program.symbols.definitions.filter { if (head) it.macro && it.start in RiscVCompletionContext.activeMacros(program.expanded.text, expandedOffset).values else !it.macro }.distinctBy { it.name }.forEach { def ->
            completions.addElement(LookupElementBuilder.create(def.name).withTypeText(if (def.macro) "macro" else "symbol"))
        }
        // Macro parameters are offered only inside their own lexical macro body.
        val before = source.take(offset)
        val macro = Regex("(?m)^\\s*\\.macro\\s+(\\S+)([^\\n]*)").findAll(before).lastOrNull()
        if (!head && macro != null && !before.substring(macro.range.last + 1).contains(Regex("(?m)^\\s*\\.endm\\b"))) {
            Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(macro.groupValues[2].substringBefore('#')).map { it.value }
                .filter { it !in setOf("req", "vararg") }.forEach { completions.addElement(LookupElementBuilder.create("\\$it").withTypeText("macro parameter")) }
        }
    }
}
