package dev.masmld.riscv.highlighting

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import dev.masmld.core.icons.AssemblyIcons

class RiscVColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "MASMLD RISC-V"
    override fun getIcon() = AssemblyIcons.FILE
    override fun getHighlighter() = RiscVSyntaxHighlighter()
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Instruction", RiscVColors.INSTRUCTION),
        AttributesDescriptor("Pseudo-instruction", RiscVColors.PSEUDO_INSTRUCTION),
        AttributesDescriptor("Assembler directive", RiscVColors.DIRECTIVE),
        AttributesDescriptor("Integer register", RiscVColors.REGISTER),
        AttributesDescriptor("Label definition", RiscVColors.LABEL),
        AttributesDescriptor("Number and local label reference", RiscVColors.NUMBER),
        AttributesDescriptor("String", RiscVColors.STRING),
        AttributesDescriptor("Line comment and preprocessor line", RiscVColors.LINE_COMMENT),
        AttributesDescriptor("Block comment", RiscVColors.BLOCK_COMMENT),
        AttributesDescriptor("Operator and separator", RiscVColors.OPERATOR),
        AttributesDescriptor("Parentheses", RiscVColors.PARENTHESES),
        AttributesDescriptor("Comma", RiscVColors.COMMA),
        AttributesDescriptor("Macro parameter", RiscVColors.MACRO_PARAMETER),
    )
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf(
        "insn" to RiscVColors.INSTRUCTION, "pseudo" to RiscVColors.PSEUDO_INSTRUCTION,
        "dir" to RiscVColors.DIRECTIVE, "reg" to RiscVColors.REGISTER, "label" to RiscVColors.LABEL,
    )
    override fun getDemoText() = """
        # GNU-style RISC-V assembly
        <dir>.section</dir> .text
        <dir>.globl</dir> _start
        <label>_start</label>:
            <pseudo>li</pseudo> <reg>a0</reg>, 42
            <insn>addi</insn> <reg>sp</reg>, <reg>sp</reg>, -16
            <insn>sd</insn> <reg>ra</reg>, 8(<reg>sp</reg>)
            <insn>beq</insn> <reg>a0</reg>, <reg>zero</reg>, 1f
        <label>1</label>: <pseudo>ret</pseudo>
        <dir>.asciz</dir> "message # not a comment"
        /* Block comment */
    """.trimIndent()
}
