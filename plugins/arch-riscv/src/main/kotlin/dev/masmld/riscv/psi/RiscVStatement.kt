package dev.masmld.riscv.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import dev.masmld.riscv.symbols.RiscVSymbolReferences

/** One statement, retaining unknown opcodes, macros and unfinished operands. */
class RiscVStatement(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReferences() = RiscVSymbolReferences.references(this)
    override fun getReference() = references.singleOrNull()
}
