package dev.masmld.riscv

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.lexer.RiscVLexer
import dev.masmld.riscv.lexer.RiscVTokens as T
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RiscVLexerTest {
    private data class Token(val type: IElementType, val text: String, val start: Int, val end: Int, val state: Int)
    private fun scan(text: String, start: Int = 0, state: Int = 0): List<Token> {
        val lexer = RiscVLexer()
        lexer.start(text, start, text.length, state)
        val tokens = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            assertTrue("Lexer must progress", lexer.tokenEnd > lexer.tokenStart)
            tokens += Token(lexer.tokenType!!, text.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.tokenEnd, lexer.state)
            lexer.advance()
        }
        return tokens
    }
    @Test fun `strings contain comments and escaped quotes`() {
        val source = ".asciz \"a#b;/*c*/\\\"d\" # real comment\nret"
        val tokens = scan(source)
        assertEquals(listOf("\"a#b;/*c*/\\\"d\""), tokens.filter { it.type == T.STRING }.map { it.text })
        assertEquals(listOf("# real comment"), tokens.filter { it.type == T.LINE_COMMENT }.map { it.text })
        assertFalse(tokens.any { it.type == T.BLOCK_COMMENT })
    }
    @Test fun `unfinished strings recover at newline`() {
        val tokens = scan(".asciz \"unfinished\naddi a0,a1,1")
        assertEquals("\"unfinished", tokens.single { it.type == T.STRING }.text)
        assertTrue(tokens.any { it.text == "addi" && it.type == T.IDENTIFIER })
        assertEquals(1, tokens.count { it.type == T.NEWLINE })
    }
    @Test fun `block comment does not expose instruction text`() {
        val tokens = scan("add /* a0\nret # */ a1, a2, a3")
        assertEquals("/* a0\nret # */", tokens.single { it.type == T.BLOCK_COMMENT }.text)
        assertFalse(tokens.any { it.text == "ret" })
        assertEquals("/* unfinished", scan("/* unfinished").single().text)
    }
    @Test fun `separators local labels relocations and GNU characters`() {
        val tokens = scan("1: beq a0,zero,1b; li a1,0x2a\r\n.word 'A, '\\n, 0b10, -16, %hi(symbol)")
        assertEquals(listOf("1b"), tokens.filter { it.type == T.LOCAL_REFERENCE }.map { it.text })
        assertEquals(listOf("'A", "'\\n"), tokens.filter { it.type == T.CHARACTER }.map { it.text })
        assertEquals(listOf("1", "0x2a", "0b10", "16"), tokens.filter { it.type == T.NUMBER }.map { it.text })
        assertEquals(1, tokens.count { it.type == T.SEPARATOR })
        assertEquals("\r\n", tokens.single { it.type == T.NEWLINE }.text)
    }
    @Test fun `preprocessor line is opaque and macros preserve parameters`() {
        val tokens = scan("#include \"header.h\"\n.macro save reg\nsd \\reg,0(sp)\n.endm")
        assertEquals("#include \"header.h\"", tokens.first().text)
        assertEquals(listOf("\\reg"), tokens.filter { it.type == T.MACRO_PARAMETER }.map { it.text })
    }
    @Test fun `restart at every token preserves the suffix`() {
        val source = ".text\r\n1: add a0, a1, a2; ret # c\n/* block\n comment */ .asciz \"x\\\"#y\"\n.word 'A\n/* open"
        val original = scan(source)
        for ((index, token) in original.withIndex()) {
            assertEquals("restart at ${token.start}", original.drop(index), scan(source, token.start, token.state))
        }
    }
    @Test fun `continuation state handles a truncated block comment`() {
        val lexer = RiscVLexer()
        val source = "/* comment continues */ add"
        lexer.start(source, 0, 7, 0)
        lexer.advance()
        assertEquals(T.BLOCK_COMMENT, scan(source, 7, lexer.state).first().type)
        assertEquals("ent continues */", scan(source, 7, lexer.state).first().text)
    }
    @Test fun `arbitrary unfinished input remains lossless and terminates`() {
        val random = Random(412)
        val chars = "abcxyz0129#/*\\\"' \n\r\t:;(),%+-\u0000"
        repeat(300) {
            val source = (0 until random.nextInt(0, 200)).map { chars[random.nextInt(chars.length)] }.joinToString("")
            val tokens = scan(source)
            assertEquals(source, tokens.joinToString("") { it.text })
            assertEquals(source.length, tokens.lastOrNull()?.end ?: 0)
        }
        assertEquals(TokenType.BAD_CHARACTER, scan("\u0000").single().type)
    }
}
