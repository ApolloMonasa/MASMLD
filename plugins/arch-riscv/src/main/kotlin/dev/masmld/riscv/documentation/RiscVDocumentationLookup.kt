package dev.masmld.riscv.documentation

import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.core.documentation.DocumentationKind as K
import dev.masmld.riscv.highlighting.RiscVClassifier
import dev.masmld.riscv.highlighting.RiscVRole as R

/** Exact token ranges prevent comments, whitespace and symbol names from becoming ISA documentation. */
object RiscVDocumentationLookup {
    fun at(source: CharSequence, offset: Int): DocumentationEntry? {
        if (offset !in 0 until source.length) return null
        val match = RiscVClassifier.classify(source).firstOrNull { offset >= it.start && offset < it.end } ?: return null
        val kind = when (match.role) {
            R.INSTRUCTION -> K.INSTRUCTION
            R.PSEUDO_INSTRUCTION -> K.PSEUDO_INSTRUCTION
            R.DIRECTIVE -> K.DIRECTIVE
            R.REGISTER -> K.REGISTER
            R.LABEL -> return null
        }
        return RiscVDocumentationCatalog.find(kind, source.subSequence(match.start, match.end).toString())
    }
}
