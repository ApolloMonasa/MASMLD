package dev.masmld.riscv

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.searches.ReferencesSearch
import dev.masmld.core.settings.AssemblySettings
import dev.masmld.core.source.SourceText

/** Real platform PSI/index/editor integration in unit-test mode; no IDE license or GUI process. */
class RiscVEditorTest : BasePlatformTestCase() {
    override fun setUp() { super.setUp(); AssemblySettings.getInstance(project).selectArchitecture("riscv") }
    fun testLocalReferencesAndFindUsages() {
        val file = myFixture.addFileToProject("entry.asm", "target: nop\nj target\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertEquals(RiscVLanguage, myFixture.file.language)
        val reference = myFixture.file.findReferenceAt(myFixture.file.text.lastIndexOf("target"))!!
        val target = reference.resolve()!!
        assertEquals("target", (target as com.intellij.psi.PsiNamedElement).name)
        assertEquals(1, ReferencesSearch.search(target).findAll().size)
    }
    fun testIncludedMacroAndIncludePathNavigate() {
        myFixture.addFileToProject("macros.inc", ".macro save reg\nnop\n.endm\n")
        val file = myFixture.addFileToProject("entry.asm", ".include \"macros.inc\"\nsave ra\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertEquals("macros.inc", myFixture.file.findReferenceAt(myFixture.file.text.lastIndexOf("save"))!!.resolve()!!.containingFile.name)
        assertEquals("macros.inc", (myFixture.file.findReferenceAt(myFixture.file.text.indexOf("macros.inc"))!!.resolve() as com.intellij.psi.PsiFile).name)
    }
    fun testMacroCompletionAndNavigationFromSiblingAssemblyFile() {
        val definition = myFixture.addFileToProject("sub.s", ".macro SAVE_SN n\n    sd s\\n, (\\n+2)*8(a0)\n.endm\n")
        for (path in listOf("sub.s", "\"sub.s\"")) {
            myFixture.configureByText("main.s", ".include $path\n    SAVE_<caret> 1")
            myFixture.completeBasic()
            myFixture.lookup?.let { lookup ->
                lookup.currentItem = lookup.items.single { it.lookupString == "SAVE_SN" }
                myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
            }
            myFixture.checkResult(".include $path\n    SAVE_SN 1")
            val file = myFixture.file
            val macro = file.findReferenceAt(file.text.indexOf("SAVE_SN"))?.resolve()
            assertEquals("SAVE_SN", (macro as? com.intellij.psi.PsiNamedElement)?.name)
            assertEquals(definition.virtualFile, macro?.containingFile?.virtualFile)
        }
    }

    fun testInstructionDiagnosticsHighlightOnlyInvalidOperands() {
        val source = """
            addi a0, x32, (0x800 + 1)
            ld a0, 4096(fa0)
            c.ld a0, 3(a1)
            c.lwsp a0, 4(a1)
            csrrwi a0, 0x1000, 32
            addi a0, a1, %hi(symbol)
            fadd.s fa0, fa1, fa2, invalid
        """.trimIndent()
        myFixture.configureByText("diagnostics.s", source)
        val expected = listOf("x32", "(0x800 + 1)", "fa0", "4096", "3", "a1", "0x1000", "32", "%hi(symbol)", "invalid")
        val warnings = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.WARNING)
            .filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.WARNING }
            .map { source.substring(it.startOffset, it.endOffset) }
        assertEquals(expected.sorted(), warnings.sorted())
    }

    fun testImmediateChecksRespectXlenAndLeaveSymbolicOperandsAlone() {
        val common = """
            addi a0, a1, 0xffffffff
            slli a0, a1, 32
            lw a0, (symbol)
            lw a0, %pcrel_lo(symbol)(sp)
            c.lwsp a0, 252(sp)
        """.trimIndent()
        assertEquals(listOf("32"), diagnosticText(common, "rv32gc"))
        assertEquals(listOf("0xffffffff"), diagnosticText(common, "rv64gc"))
    }

    private fun diagnosticText(source: String, isa: String): List<String> =
        dev.masmld.riscv.editing.RiscVDiagnostics.analyze(source, dev.masmld.riscv.editing.RiscVIsa.parse(isa))
            .map { source.substring(it.start, it.end) }

    fun testKnownConditionsFilterCompletionAndNavigationAcrossIncludes() {
        val enabled = myFixture.addFileToProject("enabled.inc", ".globl feature_on\nfeature_on: nop\n")
        myFixture.addFileToProject("disabled.inc", ".globl feature_off\nfeature_off: nop\n")
        val body = """
            .equ SHIFT, 2
            .set FLAG, 1 + SHIFT << 3
            .if FLAG == 24
            .include "disabled.inc"
            .elseif FLAG == 17
                .ifdef FLAG
                    .include "enabled.inc"
                .endif
            .else
            .include "disabled.inc"
            .endif

        """.trimIndent()
        myFixture.configureByText("conditions.s", "$body\nj feature_<caret>")
        val variants = myFixture.completeBasic()
        assertFalse(variants.orEmpty().any { it.lookupString == "feature_off" })
        assertTrue(variants.orEmpty().any { it.lookupString == "feature_on" } || myFixture.file.text.endsWith("j feature_on"))
        myFixture.configureByText("conditions.s", "$body\nj feature_on\nj feature_off")
        val file = myFixture.file
        assertEquals(enabled.virtualFile, file.findReferenceAt(file.text.lastIndexOf("feature_on"))?.resolve()?.containingFile?.virtualFile)
        assertNull(file.findReferenceAt(file.text.lastIndexOf("feature_off"))?.resolve())
    }

    fun testUnknownConditionalRetainsMacroTargetsAndMergesConstantFacts() {
        val body = """
            .if BUILD_FLAG
                .macro SHARED
                    nop
                .endm
                .macro POSSIBLE
                    nop
                .endm
                .set SAME, 1
            .else
                .macro SHARED
                    ret
                .endm
                .set SAME, 1
            .endif
            .if SAME
                .macro ACTIVE
                    nop
                .endm
            .else
                .macro INACTIVE
                    ret
                .endm
            .endif
            SHARED
        """.trimIndent()
        myFixture.configureByText("conditions.s", "$body\n")
        val file = myFixture.file
        val reference = file.findReferenceAt(file.text.lastIndexOf("SHARED")) as com.intellij.psi.PsiPolyVariantReference
        assertEquals(2, reference.multiResolve(false).size)
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(file)
        val visible = program.macroTargetsAt(file, file.textLength)
        assertEquals(setOf("SHARED", "POSSIBLE", "ACTIVE"), program.symbols.definitions.filter { it.start in visible }.map { it.name }.toSet())
    }

    fun testUnknownConditionsRetainOrdinaryLabelCandidates() {
        val source = ".if EXTERNAL\ntarget: nop\n.else\ntarget: ret\n.endif\nj target"
        myFixture.configureByText("conditions.s", source)
        val reference = myFixture.file.findReferenceAt(source.lastIndexOf("target")) as com.intellij.psi.PsiPolyVariantReference
        assertEquals(2, reference.multiResolve(false).size)
    }

    fun testUnknownConditionalPurgePreservesPossibleMacroCompletion() {
        val body = ".macro WORK\nnop\n.endm\n.if EXTERNAL\n.purgem WORK\n.endif\n"
        myFixture.configureByText("conditions.s", "${body}WOR<caret>")
        val variants = myFixture.completeBasic()
        assertTrue(variants.orEmpty().any { it.lookupString == "WORK" } || myFixture.file.text.endsWith("WORK"))
    }

    fun testMissingIncludeOnlyInvalidatesFactsOnAReachablePath() {
        val body = """
            .set VALUE, 0
            .if VALUE
                .include "missing.inc"
            .endif
            .ifeq VALUE
                active: nop
            .else
                inactive: nop
            .endif
        """.trimIndent()
        myFixture.configureByText("conditions.s", body)
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(myFixture.file)
        assertEquals(setOf("VALUE", "active"), program.symbols.definitions.map { it.name }.toSet())
    }

    fun testMacroPreviewUsesIncludedDefinitionAndDefaultValues() {
        val definition = myFixture.addFileToProject(
            "sub.s", ".macro SAVE_SN n:req, reg=a0\n    sd s\\n, (\\n+2)*8(\\reg)\n.endm\n"
        )
        myFixture.configureByText("main.s", ".include \"sub.s\"\n    SAVE_SN 1\n")
        val file = myFixture.file
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(file)
        val (reference, _) = program.references(file, 0, file.textLength).single { it.first.macroTarget != null }
        val call = requireNotNull(program.macros.callAt(reference.start))
        assertEquals(mapOf("n" to "1", "reg" to "a0"), call.bindings)
        assertEmpty(call.problems)
        assertEquals("sd s1, (1+2)*8(a0)", call.expansion().trim())
        assertEquals(definition.virtualFile, program.definition(program.symbols.resolve(reference).single())?.containingFile?.virtualFile)
    }

    fun testMacroDiagnosticsUseTheCallSiteAndActiveMacroMode() {
        val header = ".macro SAVE_SN n:req, reg=a0\n    nop\n.endm\n"
        val cases = listOf(
            "SAVE_SN" to "缺少必填宏参数：n",
            "SAVE_SN n=1,n=2" to "宏参数重复传入：n",
            "SAVE_SN n=1,other=2" to "未知的宏参数：other",
            "SAVE_SN 1,a1,a2" to "宏位置参数过多",
            ".altmacro\nSAVE_SN n=1" to ".altmacro 仅支持位置传参",
        )
        for ((source, message) in cases) {
            val text = header + source
            myFixture.configureByText("macro.s", text)
            val diagnostics = dev.masmld.riscv.editing.RiscVFileDiagnostics.get(myFixture.file)
            val problem = diagnostics.single { it.message.startsWith(message) }
            assertEquals(text.lastIndexOf("SAVE_SN"), problem.start)
            assertEquals("SAVE_SN", text.substring(problem.start, problem.end))
        }
    }

    fun testMacroPreviewStopsAtExpansionTimeConstructs() {
        for (construct in listOf(".if 1\n.endif", ".rept 2\n.endr", "OTHER", "M")) {
            val text = ".macro OTHER\nnop\n.endm\n.macro M n=3\n.word \\n\\().x\n$construct\nnop\n.endm\nM"
            myFixture.configureByText("macro.s", text)
            val program = dev.masmld.riscv.symbols.RiscVProgram.get(myFixture.file)
            val preview = requireNotNull(program.macros.callAt(text.lastIndex)).expansion()
            assertTrue(preview, preview.startsWith(".word 3.x\n⟪预览在此停止⟫ "))
            assertTrue(preview, preview.endsWith(construct.substringBefore('\n') + "\n"))
        }
    }

    fun testMacroPreviewUsesModeAtCallAndAcceptsTrailingComma() {
        val text = ".altmacro\n.macro M n:req\n.word n, \\n\n.endm\n.noaltmacro\nM 1,\n.altmacro\nM 2,"
        myFixture.configureByText("macro.s", text)
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(myFixture.file)
        val calls = program.symbols.references.filter { it.macroTarget != null }
        assertEquals(listOf(".word n, 1", ".word 2, 2"), calls.map {
            val call = requireNotNull(program.macros.callAt(it.start))
            assertEmpty(call.problems)
            call.expansion().trim()
        })
    }

    fun testAlternateMacroPreviewNormalizesLiteralsAndKeepsExpressionsSymbolic() {
        val values = listOf("%0x10" to "16", "%010" to "8", "%0b11" to "3", "%-2" to "-2",
            "%(n + 2)" to "⟨(n + 2)⟩", "%2+3" to "⟨2+3⟩")
        val header = ".altmacro\n.macro M n\n.word \\n\n.endm\n"
        myFixture.configureByText("macro.s", header + values.joinToString("\n") { (value, _) -> "M $value" })
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(myFixture.file)
        val calls = program.symbols.references.filter { it.macroTarget != null }
        assertEquals(values.map { ".word ${it.second}" }, calls.map {
            requireNotNull(program.macros.callAt(it.start)).expansion().trim()
        })
    }

    fun testMacroPreviewAtRepeatArgumentUsesSymbolicValue() {
        val source = ".altmacro\n.macro SAVE_GP n\n    sd x\\n, \\n*8(sp)\n.endm\n" +
            ".set n, 5\n.rept 27\n    SAVE_GP %n<caret>\n.set n, n+1\n.endr"
        myFixture.configureByText("trap.S", source)
        val program = dev.masmld.riscv.symbols.RiscVProgram.get(myFixture.file)
        val call = requireNotNull(program.macros.callAt(myFixture.file, myFixture.caretOffset))
        assertEquals(mapOf("n" to "⟨n⟩"), call.bindings)
        assertEquals("sd x⟨n⟩, ⟨n⟩*8(sp)", call.expansion().trim())
    }

    fun testMacroDiagnosticsSkipUncertainActiveDefinitions() {
        for (guard in listOf(".if 1\n.endif", "#ifdef TARGET\n#endif", ".include \"missing.s\"")) {
            myFixture.configureByText("macro.S", "$guard\n.macro M n:req\nnop\n.endm\nM")
            assertEmpty(dev.masmld.riscv.editing.RiscVFileDiagnostics.get(myFixture.file))
        }
    }

    fun testSymbolCompletionAndConservativeReformat() {
        myFixture.configureByText("entry.asm", "target: nop\nj tar<caret>")
        val variants = myFixture.completeBasic()
        assertTrue(variants?.any { it.lookupString == "target" } == true || myFixture.file.text.endsWith("j target"))
        myFixture.configureByText("format.asm", "target: add a0,a1,a2\n.asciz \"a,b\"\n")
        val before = SourceText.tokens(myFixture.file.text).map { it.text }
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
        assertEquals(before, SourceText.tokens(myFixture.file.text).map { it.text })
        assertTrue(myFixture.file.text.contains("a0, a1, a2"))
        assertTrue(myFixture.file.text.contains("\"a,b\""))
    }
    fun testDirectiveCompletionReplacesTheLeadingDot() {
        for (prefix in listOf(".", ".2b")) {
            myFixture.configureByText("directive.asm", "label: ${prefix}<caret>")
            myFixture.completeBasic()
            myFixture.lookup?.let { lookup ->
                lookup.currentItem = lookup.items.single { it.lookupString == ".2byte" }
                myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
            }
            myFixture.checkResult("label: .2byte")
        }
    }
    fun testDirectiveCompletionInTheMiddlePreservesOperands() {
        for (key in listOf(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR, com.intellij.codeInsight.lookup.Lookup.REPLACE_SELECT_CHAR)) {
            myFixture.configureByText("directive.asm", ".2by<caret>te 1")
            myFixture.completeBasic()
            requireNotNull(myFixture.lookup).let { lookup ->
                lookup.currentItem = lookup.items.single { it.lookupString == ".2byte" }
                myFixture.finishLookup(key)
            }
            myFixture.checkResult(".2byte 1")
        }
    }

    fun testDottedLocalLabelCompletionInOperand() {
        myFixture.configureByText("loop.asm", ".Lloop: nop\nj .L<caret>")
        myFixture.completeBasic()
        myFixture.lookup?.let { lookup ->
            lookup.currentItem = lookup.items.single { it.lookupString == ".Lloop" }
            myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
        }
        myFixture.checkResult(".Lloop: nop\nj .Lloop")
    }

    fun testTypingEndmDedentsOnlyTheDirective() {
        for (indent in listOf("    ", "\t\t")) {
            val body = ".macro HELLO\n    nop\n"
            myFixture.configureByText("macro.asm", body + indent + "<caret>")
            myFixture.type(".endm")
            myFixture.checkResult(body + ".endm<caret>")
        }
        myFixture.configureByText("macro.asm", "/*\n    <caret>\n*/")
        myFixture.type(".endm")
        myFixture.checkResult("/*\n    .endm<caret>\n*/")
    }

    fun testEndmDedentsWhileCompletionIsOpen() {
        val body = ".macro .endother\n.endm\n.macro HELLO\n    nop\n"
        myFixture.configureByText("macro.asm", body + "    .end<caret>")
        myFixture.completeBasic()
        requireNotNull(myFixture.lookup)
        myFixture.type('m')
        myFixture.checkResult(body + ".endm<caret>")
    }

    fun testAcceptingEndmCompletionDedents() {
        val body = ".macro .endother\n.endm\n.macro HELLO\n    nop\n"
        myFixture.configureByText("macro.asm", body + "    .end<caret>")
        myFixture.completeBasic()
        val lookup = requireNotNull(myFixture.lookup)
        lookup.currentItem = lookup.items.single { it.lookupString == ".endm" }
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
        myFixture.checkResult(body + ".endm<caret>")
    }

    fun testEnterAfterMacroHeaderUsesOneIndent() {
        myFixture.configureByText("macro.asm", ".macro HELLO<caret>\n.endm")
        val settings = com.intellij.application.options.CodeStyle.getSettings(project).clone()
        settings.getIndentOptions(myFixture.file.fileType).apply {
            INDENT_SIZE = 4
            TAB_SIZE = 4
            CONTINUATION_INDENT_SIZE = 8
            USE_TAB_CHARACTER = true
        }
        com.intellij.application.options.CodeStyle.doWithTemporarySettings(project, settings, Runnable {
            myFixture.type('\n')
            myFixture.type(".p2align 2")
            assertEquals(".macro HELLO\n    .p2align 2\n.endm", myFixture.editor.document.text.replace("\t", "    "))
        })
    }

    fun testEnterAfterStandaloneLabelUsesOneIndent() {
        for (label in listOf("__alltraps:", ".Lloop: # comment", "1:")) {
            myFixture.configureByText("label.s", "$label<caret>")
            val settings = com.intellij.application.options.CodeStyle.getSettings(project).clone()
            settings.getIndentOptions(myFixture.file.fileType).apply {
                INDENT_SIZE = 4
                TAB_SIZE = 4
                USE_TAB_CHARACTER = true
            }
            com.intellij.application.options.CodeStyle.doWithTemporarySettings(project, settings, Runnable {
                myFixture.type('\n')
                myFixture.type("nop")
                assertEquals("$label\n    nop", myFixture.editor.document.text.replace("\t", "    "))
            })
        }
    }

    fun testEnterUsesStatementIndentInsteadOfContinuationIndent() {
        val cases = listOf(
            ".text" to "",
            "__alltraps: nop" to "",
            "    addi a0, a0, 1" to "    ",
            ".macro HELLO\n    .p2align 2" to "    "
        )
        for ((source, indent) in cases) {
            myFixture.configureByText("indent.asm", source + "<caret>")
            myFixture.type('\n')
            assertEquals(source + "\n" + indent, myFixture.editor.document.text)
        }
    }

    fun testEnterAfterIncludeReturnsToTopLevel() {
        myFixture.addFileToProject("trap.S", ".macro SAVE_GP\n    nop\n.endm\n")
        for (indent in listOf("", "    ")) {
            val include = indent + ".include \"trap.S\""
            myFixture.configureByText("entry.S", "$include<caret>")
            myFixture.type('\n')
            myFixture.type("SAVE_GP")
            myFixture.checkResult("$include\nSAVE_GP<caret>")
        }
    }

    fun testEnterAfterEndmReturnsToTopLevel() {
        val source = """
            .altmacro
            .macro SAVE_SN n
                sd s\n, (\n+2)*8(a0)
            .endm
            .macro LOAD_SN n
                ld s\n, (\n+2)*8(a1)
            .endm
        """.trimIndent()
        myFixture.configureByText("macro.asm", source + "<caret>")
        myFixture.type('\n')
        myFixture.checkResult(source + "\n<caret>")
    }
    fun testMacroRenamePreservesLabelNamespace() {
        myFixture.configureByText("rename.asm", ".macro <caret>save reg\nnop\n.endm\nsave: nop\nsave ra\nj save\n.purgem save\n")
        myFixture.renameElementAtCaret("push_reg")
        assertTrue(myFixture.file.text.contains(".macro push_reg"))
        assertTrue(myFixture.file.text.contains("push_reg ra"))
        assertTrue(myFixture.file.text.contains(".purgem push_reg"))
        assertTrue(myFixture.file.text.contains("save: nop"))
        assertTrue(myFixture.file.text.contains("j save"))
    }
    fun testProjectExportCompletionAndCrossFileRename() {
        myFixture.addFileToProject("library.asm", ".globl external_fn\nexternal_fn: ret\n")
        val caller = myFixture.addFileToProject("caller.asm", "call external_fn\n")
        myFixture.configureByText("completion.asm", "call external_<caret>")
        val variants = myFixture.completeBasic()
        assertTrue(variants?.any { it.lookupString == "external_fn" } == true || myFixture.file.text.contains("call external_fn"))
        myFixture.configureFromExistingVirtualFile(caller.virtualFile)
        val target = myFixture.file.findReferenceAt(myFixture.file.text.indexOf("external_fn"))!!.resolve()!!
        assertEquals("library.asm", target.containingFile.name)
        com.intellij.refactoring.rename.RenameProcessor(project, target, "renamed_fn", false, false).run()
        assertTrue(caller.text.contains("call renamed_fn"))
    }
    fun testIncludeEditsInvalidateResolution() {
        val included = myFixture.addFileToProject("constants.inc", ".equ VALUE, 1\n")
        val root = myFixture.addFileToProject("entry.asm", ".include \"constants.inc\"\nli a0, VALUE\n")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)
        assertNotNull(myFixture.file.findReferenceAt(myFixture.file.text.lastIndexOf("VALUE"))!!.resolve())
        WriteCommandAction.runWriteCommandAction(project) {
            val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(included)!!
            document.setText(".equ OTHER, 1\n")
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        assertNull(myFixture.file.findReferenceAt(myFixture.file.text.lastIndexOf("VALUE"))!!.resolve())
    }

    fun testNumericRenameIsRejectedBeforeAnyEdits() {
        myFixture.configureByText("numeric.asm", "1: nop\nj 1b\n")
        val original = myFixture.file.text
        val definition = myFixture.file.findElementAt(0)!!.parent
        org.junit.Assert.assertThrows(com.intellij.util.IncorrectOperationException::class.java) {
            WriteCommandAction.runWriteCommandAction(project) { dev.masmld.riscv.editing.RiscVRename().renameElement(definition, "named", emptyArray(), null) }
        }
        assertEquals(original, myFixture.file.text)
    }
    fun testRepeatedConstantAssignmentsRenameTogether() {
        myFixture.configureByText("constants.asm", ".set <caret>VALUE,1\n.word VALUE\n.set VALUE,2\n.word VALUE\n")
        myFixture.renameElementAtCaret("COUNT")
        assertFalse(myFixture.file.text.contains("VALUE"))
        assertEquals(4, Regex("COUNT").findAll(myFixture.file.text).count())
    }

    fun testNumericFindUsagesSelectsOnlyTheNearestDefinition() {
        myFixture.configureByText("numeric.asm", "1: nop\nj 1b\n1: ret\nj 1b\n")
        val definitions = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(myFixture.file, dev.masmld.riscv.psi.RiscVSymbolDefinition::class.java).toList()
        assertEquals(2, definitions.size)
        assertEquals(1, ReferencesSearch.search(definitions[0]).findAll().size)
        assertEquals(1, ReferencesSearch.search(definitions[1]).findAll().size)
    }

    fun testIncludeFilenameRenamePreservesDirectoryAndUpdatesEveryUse() {
        val included = myFixture.addFileToProject("dir/shared.inc", "# fixture\n")
        val root = myFixture.addFileToProject("root.asm", ".include \"dir/shared.inc\"\n.include \"dir/shared.inc\"\n")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)
        com.intellij.refactoring.rename.RenameProcessor(project, included, "renamed long file.inc", false, false).run()
        assertFalse(root.text.contains("shared.inc"))
        assertEquals(2, Regex("dir/renamed long file.inc").findAll(root.text).count())
    }

}
