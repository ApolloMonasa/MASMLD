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

    fun testEnterUsesStatementIndentInsteadOfContinuationIndent() {
        val cases = listOf(
            ".text" to "",
            "    addi a0, a0, 1" to "    ",
            ".macro HELLO\n    .p2align 2" to "    "
        )
        for ((source, indent) in cases) {
            myFixture.configureByText("indent.asm", source + "<caret>")
            myFixture.type('\n')
            assertEquals(source + "\n" + indent, myFixture.editor.document.text)
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
