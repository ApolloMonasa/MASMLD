package dev.masmld.ld.editing

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import dev.masmld.ld.documentation.LdDocumentationCatalog
import dev.masmld.ld.symbols.*

class LdCompletion : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        val inert = dev.masmld.core.source.SourceText.tokens(file.text, true).firstOrNull { parameters.offset > it.start && parameters.offset < it.end }
        if (inert?.kind in setOf(dev.masmld.core.source.SourceToken.Kind.COMMENT, dev.masmld.core.source.SourceToken.Kind.STRING)) return
        val prefix = file.text.take(parameters.offset.coerceAtMost(file.textLength))
        if (prefix.substringAfterLast('\n').contains('#') || prefix.lastIndexOf("/*") > prefix.lastIndexOf("*/")) return
        val region = Regex("(?:>\\s*|\\b(?:ORIGIN|LENGTH)\\s*\\(\\s*)[\\w.$-]*$").containsMatchIn(prefix)
        val section = Regex("\\b(?:ADDR|LOADADDR|SIZEOF|ALIGNOF)\\s*\\(\\s*[\\w.$-]*$").containsMatchIn(prefix)
        if (!region && !section) LdDocumentationCatalog.entries.forEach { result.addElement(LookupElementBuilder.create(it.name).withTypeText(it.kind.label).withTailText("  ${it.syntax}", true)) }
        LdProgram.get(file).symbols.definitions.filter { when { region -> it.kind == LdSymbolKind.REGION; section -> it.kind == LdSymbolKind.SECTION; else -> true } }.distinctBy { it.name }.forEach {
            result.addElement(LookupElementBuilder.create(it.name).withTypeText(it.kind.name.lowercase()))
        }
    }
}
