package dev.masmld.ld.map

import com.intellij.openapi.progress.ProgressManager
import dev.masmld.ld.symbols.LdSymbolKind

internal enum class MapKind(val label: String, val scriptKind: LdSymbolKind?) {
    OUTPUT("输出段", LdSymbolKind.SECTION),
    INPUT("输入段", null),
    SYMBOL("符号", LdSymbolKind.SYMBOL),
}

internal data class MapEntry(
    val kind: MapKind,
    val name: String,
    val address: ULong,
    val line: Int,
    val size: ULong? = null,
    val loadAddress: ULong? = null,
    val objectFile: String? = null,
    val outputLine: Int? = null,
    val inputSection: String? = null,
)

internal class GnuLinkerMap(val entries: List<MapEntry>) {
    private val outputsByLine = entries.filter { it.kind == MapKind.OUTPUT }.associateBy { it.line }
    private val inputsByOutput = entries.filter { it.kind == MapKind.INPUT }.groupBy { it.outputLine }

    fun output(entry: MapEntry): MapEntry? = outputsByLine[entry.outputLine]

    fun inputs(entry: MapEntry): List<MapEntry> = inputsByOutput[entry.line].orEmpty()

    companion object {
        const val MAX_BYTES = 16 * 1024 * 1024
        private const val HEX = """0[xX][0-9A-Fa-f]+"""
        private const val NAME = """[^\s=()]+"""
        private val section = Regex("""(\h*)($NAME)\h+($HEX)\h+($HEX)(?:\h+(.*))?""")
        private val wrappedName = Regex("""(\h*)($NAME)""")
        private val continuation = Regex("""\h+($HEX)\h+($HEX)(?:\h+(.*))?""")
        private val address = Regex("""\h+($HEX)\h+(?:\[$HEX]\h+)?(.+)""")
        private val loadAddress = Regex("""load address\h+($HEX)(?:\h+.*)?""")
        private val assignment = Regex("""(?:PROVIDE(?:_HIDDEN)?\h*\(\h*)?($NAME)\h*=.*""")
        private val bareName = Regex("""[\p{L}_.$][\p{L}\p{N}_.$@+-]*""")

        fun parse(text: CharSequence): GnuLinkerMap? {
            if (text.length > MAX_BYTES) return null
            val entries = mutableListOf<MapEntry>()
            var started = false
            var output: MapEntry? = null
            var input: MapEntry? = null
            var wrapped: Triple<Int, String, Boolean>? = null

            fun addSection(line: Int, name: String, isInput: Boolean, start: String, length: String, tail: String) {
                val value = hex(start) ?: return
                val size = hex(length) ?: return
                if (isInput) {
                    if (output == null || tail.isBlank() || tail.startsWith("load address")) return
                    input = MapEntry(MapKind.INPUT, name, value, line, size, objectFile = tail, outputLine = output?.line)
                    entries += requireNotNull(input)
                } else {
                    if (tail.isNotEmpty() && !tail.startsWith("load address")) return
                    val lma = loadAddress.matchEntire(tail)?.groupValues?.get(1)?.let(::hex)
                    output = MapEntry(MapKind.OUTPUT, name, value, line, size, lma)
                    input = null
                    entries += requireNotNull(output)
                }
            }

            for ((lineNumber, raw) in text.lineSequence().withIndex()) {
                ProgressManager.checkCanceled()
                val line = raw.trimEnd()
                if (!started) {
                    started = line.trim() == "Linker script and memory map"
                    continue
                }
                if (line == "Cross Reference Table") break
                val pending = wrapped
                wrapped = null
                if (pending != null) {
                    val match = continuation.matchEntire(line)
                    if (match != null) {
                        val (start, length, tail) = match.destructured
                        addSection(pending.first, pending.second, pending.third, start, length, tail)
                        continue
                    }
                }
                val sectionMatch = section.matchEntire(line)
                if (sectionMatch != null) {
                    val (indent, name, start, length, tail) = sectionMatch.destructured
                    if (hex(name) == null && name != "*fill*" && name != "*") {
                        addSection(lineNumber, name, indent.isNotEmpty(), start, length, tail)
                        continue
                    }
                }
                val addressMatch = address.matchEntire(line)
                if (addressMatch != null) {
                    val (start, payload) = addressMatch.destructured
                    val value = hex(start) ?: continue
                    val assigned = assignment.matchEntire(payload)?.groupValues?.get(1)
                    val name = assigned ?: payload.takeIf(bareName::matches) ?: continue
                    if (name == ".") continue
                    val origin = input?.takeIf { assigned == null && value >= it.address && value - it.address < (it.size ?: 0uL) }
                    entries += MapEntry(MapKind.SYMBOL, name, value, lineNumber, objectFile = origin?.objectFile,
                        outputLine = output?.line, inputSection = origin?.name)
                    continue
                }
                val candidate = wrappedName.matchEntire(line)
                if (candidate != null) {
                    val (indent, name) = candidate.destructured
                    if (hex(name) == null && name != "*fill*" && name != "*") wrapped = Triple(lineNumber, name, indent.isNotEmpty())
                }
            }
            return if (started) GnuLinkerMap(entries) else null
        }

        private fun hex(value: String): ULong? = if (value.startsWith("0x", true)) value.drop(2).toULongOrNull(16) else null
    }
}

internal fun ULong.mapHex() = "0x" + toString(16)
