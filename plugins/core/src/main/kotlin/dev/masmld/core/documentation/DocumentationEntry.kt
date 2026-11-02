package dev.masmld.core.documentation

enum class DocumentationKind(val label: String) {
    INSTRUCTION("指令"), PSEUDO_INSTRUCTION("伪指令"), REGISTER("寄存器"), DIRECTIVE("汇编指示符"),
    LINKER_COMMAND("链接脚本命令/属性"), BUILTIN_FUNCTION("链接脚本内建函数")
}

data class DocumentationSource(
    val id: String,
    val title: String,
    val url: String,
    val revision: String,
    val notice: String,
)

/** Architecture-neutral offline content. All fields contain plain text, never trusted HTML. */
data class DocumentationEntry(
    val name: String,
    val kind: DocumentationKind,
    val syntax: String,
    val summary: String,
    val notes: String,
    val example: String,
    val applicability: String,
    val sources: List<DocumentationSource>,
)
