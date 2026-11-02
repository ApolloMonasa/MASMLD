package dev.masmld.ld.documentation

import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.core.documentation.DocumentationKind
import dev.masmld.core.documentation.DocumentationSource

object LdDocumentationCatalog {
    val entries: List<DocumentationEntry> by lazy {
        val stream = checkNotNull(javaClass.getResourceAsStream("/catalog/ld/entries.tsv"))
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
                val row = line.split('\t')
                require(row.size == 7) { "Invalid GNU ld documentation row" }
                DocumentationEntry(row[1], DocumentationKind.valueOf(row[0]), row[2], row[3], row[4], row[5],
                    "GNU ld；具体行为可能依赖目标和链接选项", listOf(DocumentationSource(
                        "gnu-ld", "GNU ld: ${row[1]}", row[6], "2026-11-02 查阅的在线版本",
                        "事实参考；中文摘要和示例由 MASMLD 原创，未打包上游手册正文")))
            }.toList().also { require(it.map { e -> e.kind to e.name }.distinct().size == it.size) }
        }
    }
    private val index by lazy { entries.associateBy { it.kind to it.name } }
    fun find(kind: DocumentationKind, name: String): DocumentationEntry? = index[kind to name]
}
