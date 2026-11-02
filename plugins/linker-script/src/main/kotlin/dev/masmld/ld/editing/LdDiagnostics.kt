package dev.masmld.ld.editing

import com.intellij.lang.annotation.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import dev.masmld.ld.symbols.*

data class LdProblem(val start: Int, val end: Int, val message: String)
object LdDiagnostics {
    fun analyze(symbols: LdSymbols): List<LdProblem> {
        val problems = mutableListOf<LdProblem>()
        symbols.definitions.groupBy { it.kind to it.name }.values.filter { it.size > 1 }.forEach { definitions ->
            // GNU ld intentionally permits reassignment of ordinary symbols and PROVIDE fallbacks.
            if (definitions.first().kind != LdSymbolKind.SYMBOL) definitions.drop(1).forEach { problems += LdProblem(it.start, it.end, "重复的区域或输出节名称：${it.name}") }
        }
        symbols.references.filter { it.kind == LdSymbolKind.REGION && symbols.resolve(it).isEmpty() }.forEach { problems += LdProblem(it.start, it.end, "未定义的 MEMORY 区域：${it.name}") }
        val known = symbols.regions.mapNotNull { region ->
            val start = region.origin?.let { symbols.evaluate(it) } ?: return@mapNotNull null
            val length = region.length?.let { symbols.evaluate(it) } ?: return@mapNotNull null
            if (start < 0 || length <= 0) problems += LdProblem(region.definition.start, region.definition.end, "MEMORY 起点必须非负且长度必须为正")
            val end = try { Math.addExact(start, length) } catch (_: ArithmeticException) {
                problems += LdProblem(region.definition.start, region.definition.end, "MEMORY 地址范围溢出"); return@mapNotNull null
            }
            Triple(region.definition, start, end)
        }
        for (i in known.indices) for (j in 0 until i) {
            val a = known[i]; val b = known[j]
            if (a.second < b.third && b.second < a.third) problems += LdProblem(a.first.start, a.first.end, "MEMORY 区域与 ${b.first.name} 重叠（某些目标可能有意使用地址别名）")
        }
        for (section in symbols.sections) {
            val address = section.address?.let { symbols.evaluate(it) } ?: continue
            val region = section.region ?: continue
            val origin = symbols.evaluate("ORIGIN($region)") ?: continue
            val length = symbols.evaluate("LENGTH($region)") ?: continue
            val end = try { Math.addExact(origin, length) } catch (_: ArithmeticException) { continue }
            if (address < origin || address >= end) problems += LdProblem(section.definition.start, section.definition.end, "输出节固定起点不在 MEMORY 区域 $region 内")
            val alignment = section.alignment?.let { symbols.evaluate(it) }
            if (alignment != null && alignment > 0 && address % alignment != 0L) problems += LdProblem(section.definition.start, section.definition.end, "输出节固定起点与显式 ALIGN 不一致；请核对实际链接布局")
        }
        return problems
    }
}
class LdAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is LdStatement) return
        val program = LdProgram.get(element.containingFile)
        for (problem in program.problems) {
            val site = program.expanded.site(problem.start) ?: continue
            if (program.files[site.unit.id] != element.containingFile || site.offset < element.textRange.startOffset || site.offset + problem.end - problem.start > element.textRange.endOffset) continue
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, problem.message).range(TextRange(site.offset, site.offset + problem.end - problem.start)).create()
        }
    }
}
