package dev.masmld.riscv.editing

import com.intellij.lang.parameterInfo.*
import com.intellij.psi.util.PsiTreeUtil
import dev.masmld.riscv.psi.RiscVStatement
import dev.masmld.riscv.symbols.RiscVProgram

class RiscVParameterInfo : ParameterInfoHandler<RiscVStatement, String> {
    private fun statement(context: ParameterInfoContext): RiscVStatement? = context.file.findElementAt((context.offset - 1).coerceAtLeast(0))?.let {
        PsiTreeUtil.getParentOfType(it, RiscVStatement::class.java)
    }
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): RiscVStatement? {
        val owner = statement(context) ?: return null
        val name = owner.text.trim().replace(Regex("^(?:[\\w.$]+:\\s*)+"), "").substringBefore(' ').substringBefore('\t')
        val signature = RiscVDiagnostics.signature(name) ?: RiscVProgram.get(context.file).symbols.definitions.firstOrNull { it.macro && it.name == name }?.let { definition ->
            val program = RiscVProgram.get(context.file)
            val start = program.expanded.text.lastIndexOf('\n', definition.start).let { if (it < 0) 0 else it + 1 }
            program.expanded.text.substring(start).substringBefore('\n').trim().removePrefix(".macro").trim()
        } ?: return null
        context.itemsToShow = arrayOf(signature)
        return owner
    }
    override fun showParameterInfo(element: RiscVStatement, context: CreateParameterInfoContext) { context.showHint(element, element.textOffset, this) }
    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext) = statement(context)
    override fun updateParameterInfo(element: RiscVStatement, context: UpdateParameterInfoContext) {
        if (element != context.parameterOwner) { context.removeHint(); return }
        context.setCurrentParameter(element.text.take((context.offset - element.textOffset).coerceIn(0, element.textLength)).count { it == ',' })
    }
    override fun updateUI(signature: String, context: ParameterInfoUIContext) {
        context.setupUIComponentPresentation(signature, -1, -1, false, false, false, context.defaultParameterColor)
    }
}
