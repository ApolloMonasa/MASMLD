package dev.masmld.riscv.documentation

import dev.masmld.core.documentation.DocumentationEntry
import dev.masmld.core.documentation.DocumentationKind
import dev.masmld.core.documentation.DocumentationSource

/** Only local resources are read. Source links are optional navigation, never fetched on hover. */
object RiscVDocumentationCatalog {
    private fun rows(file: String, width: Int): List<List<String>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/catalog/riscv/$file")) { "Missing catalog: $file" }
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith('#') }.mapIndexed { index, line ->
                line.split('\t').also { require(it.size == width) { "$file row ${index + 1}: expected $width fields" } }
            }.toList()
        }
    }
    private val sources by lazy {
        rows("sources.tsv", 5).associate { it[0] to DocumentationSource(it[0], it[1], it[2], it[3], it[4]) }
    }
    val entries: List<DocumentationEntry> by lazy {
        val instructions = rows("entries.tsv", 8).map { row ->
            DocumentationEntry(row[1], DocumentationKind.valueOf(row[0]), row[2], row[3], row[4].takeUnless { it == "-" }.orEmpty(),
                row[5].takeUnless { it == "-" }.orEmpty(), row[6], row[7].split(',').map { sources.getValue(it) })
        }
        val registers = rows("registers.tsv", 4).flatMap { row ->
            val names = listOf("x${row[0]}") + row[1].split('/')
            names.map { name ->
                DocumentationEntry(name, DocumentationKind.REGISTER, "$name — x${row[0]} (${row[1]})",
                    row[2], "调用约定：${row[3]}。用途约定不等于硬件限制；x0 的恒零行为是硬件语义。",
                    if (row[0] == "0") "addi a0, zero, 1" else "addi $name, $name, 0",
                    "RV32I / RV64I；标准整数调用约定", listOf(sources.getValue("abi")))
            }
        }
        (instructions + registers).also { all ->
            require(all.map { it.kind to it.name }.distinct().size == all.size) { "Duplicate catalog entry" }
        }
    }
    private val index by lazy { entries.associateBy { it.kind to it.name } }
    fun find(kind: DocumentationKind, name: String): DocumentationEntry? = index[kind to name]
}
