package dev.masmld.riscv.symbols

/** Name-only decoding for plain Itanium and Rust legacy paths. Complex ABIs use the configured demangler. */
object SymbolNames {
    fun sourceName(name: String): String? {
        if (!name.startsWith("_Z")) return null
        var i = 2
        val nested = name.getOrNull(i) == 'N'
        if (nested) i++
        while (name.getOrNull(i) in listOf('K', 'V', 'r', 'R', 'O')) i++
        val parts = mutableListOf<String>()
        while (i < name.length && name[i].isDigit()) {
            val start = i
            while (name.getOrNull(i)?.isDigit() == true) i++
            val length = name.substring(start, i).toIntOrNull() ?: return null
            if (length <= 0 || i + length > name.length) return null
            parts += name.substring(i, i + length)
            i += length
            if (!nested) break
        }
        if (parts.isEmpty() || nested && name.getOrNull(i) != 'E') return null
        if (parts.last().matches(Regex("h[0-9a-f]{16}"))) parts.removeLast()
        return parts.takeIf { it.isNotEmpty() }?.joinToString("::")
    }
}
data class SourceAlias(val external: String, val source: String, val offset: Int)
object SourceAliases {
    fun analyze(source: String, rust: Boolean, linker: Boolean): List<SourceAlias> {
        // Strip C/Rust comments without discarding Rust #[attributes] or strings.
        val masked = StringBuilder(source)
        var i = 0
        while (i < source.length) {
            if (source[i] == '"') { i++; while (i < source.length && source[i] != '"') { if (source[i] == '\\') i++; i++ }; i++; continue }
            if (source.startsWith("//", i) || source.startsWith("/*", i)) {
                val end = if (source.startsWith("//", i)) source.indexOf('\n', i).takeIf { it >= 0 } ?: source.length
                    else source.indexOf("*/", i + 2).takeIf { it >= 0 }?.plus(2) ?: source.length
                for (j in i until end) if (masked[j] != '\n') masked.setCharAt(j, ' ')
                i = end
            } else i++
        }
        val text = masked.toString()
        if (rust) return Regex("#\\[\\s*(?:unsafe\\(\\s*)?export_name\\s*=\\s*\"([^\"\\\\]+)\"\\s*\\)?\\]\\s*(?:(?:pub(?:\\([^)]*\\))?|unsafe|extern\\s*\"[^\"]*\")\\s+)*fn\\s+([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(text).map { SourceAlias(it.groupValues[1], it.groupValues[2], it.groups[2]!!.range.first) }.toList()
        if (linker) return Regex("(?m)([A-Za-z_.$][A-Za-z0-9_.$-]*)\\s*=\\s*([A-Za-z_.$][A-Za-z0-9_.$-]*)\\s*[;)]")
            .findAll(text).map { SourceAlias(it.groupValues[1], it.groupValues[2], it.groups[1]!!.range.first) }.toList()
        return Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;{}]*?\\)\\s*(?:__asm__|__asm|asm)\\s*\\(\\s*\"([^\"\\\\]+)\"\\s*\\)")
            .findAll(text).map { SourceAlias(it.groupValues[2], it.groupValues[1], it.groups[1]!!.range.first) }.toList()
    }
}
