package dev.masmld.ld

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager

class LdEditorTest : BasePlatformTestCase() {
    fun testGnuMapResolvesSymbolsAndSectionsInIncludedScript() {
        val included = myFixture.addFileToProject("layout.inc", """
            SECTIONS {
                .text : { *(.text*) __text_end = .; }
                .very_long_output_section_name : { BYTE(1); }
            }
        """.trimIndent())
        myFixture.configureByText("app.ld", "INCLUDE layout.inc")
        val source = requireNotNull(javaClass.getResource("/fixtures/gnu-ld.map")).readText()
        val map = requireNotNull(dev.masmld.ld.map.GnuLinkerMap.parse(source))
        for (name in listOf(".very_long_output_section_name", "__text_end")) {
            val entry = map.entries.single { it.name == name }
            val target = dev.masmld.ld.map.scriptTargets(myFixture.file, entry).single()
            assertEquals(included.virtualFile, target.containingFile.virtualFile)
            assertEquals(name, included.text.substring(target.textOffset, target.textOffset + name.length))
        }
        assertEmpty(dev.masmld.ld.map.scriptTargets(myFixture.file, map.entries.single { it.name == "member_entry" }))
    }

    fun testRegionReferenceAndIncludeNavigation() {
        myFixture.addFileToProject("memory.inc", "MEMORY { RAM : ORIGIN = 0x8000, LENGTH = 16K }\n")
        val file = myFixture.addFileToProject("app.ld", "INCLUDE memory.inc\nSECTIONS { .text : { *(.text) } > RAM }\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        assertEquals("memory.inc", myFixture.file.findReferenceAt(myFixture.file.text.lastIndexOf("RAM"))!!.resolve()!!.containingFile.name)
        assertEquals("memory.inc", (myFixture.file.findReferenceAt(myFixture.file.text.indexOf("memory.inc"))!!.resolve() as com.intellij.psi.PsiFile).name)
    }
    fun testSymbolRenameAndReformat() {
        myFixture.configureByText("app.ld", "<caret>BASE=0x8000;\nEND=BASE+16;\n")
        myFixture.renameElementAtCaret("START")
        assertTrue(myFixture.file.text.replace(Regex("\\s+"), "").contains("START=0x8000"))
        assertTrue(myFixture.file.text.replace(Regex("\\s+"), "").contains("END=START+16"))
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
        assertTrue(myFixture.file.text.contains("START = 0x8000"))
    }
    fun testRegionCompletionIsContextual() {
        myFixture.configureByText("app.ld", "MEMORY { RAM : ORIGIN = 0, LENGTH = 1K }\nSECTIONS { .text : { } > RA<caret> }")
        val variants = myFixture.completeBasic()
        assertTrue(variants?.any { it.lookupString == "RAM" } == true || myFixture.file.text.contains("> RAM"))
    }
    fun testReassignedSymbolsRenameAsAGroup() {
        myFixture.configureByText("reassign.ld", "<caret>BASE=0; FIRST=BASE; BASE=32; LAST=BASE;\n")
        myFixture.renameElementAtCaret("START")
        assertFalse(myFixture.file.text.contains("BASE"))
        assertEquals(4, Regex("START").findAll(myFixture.file.text).count())
    }

    fun testIncludeFilenameRenamePreservesDirectoryAndUpdatesEveryUse() {
        val included = myFixture.addFileToProject("dir/shared.inc", "# fixture\n")
        val root = myFixture.addFileToProject("root.ld", "INCLUDE dir/shared.inc\nINCLUDE dir/shared.inc\n")
        myFixture.configureFromExistingVirtualFile(root.virtualFile)
        com.intellij.refactoring.rename.RenameProcessor(project, included, "renamed long file.inc", false, false).run()
        assertFalse(root.text.contains("shared.inc"))
        assertEquals(2, Regex("dir/renamed long file.inc").findAll(root.text).count())
    }

}
