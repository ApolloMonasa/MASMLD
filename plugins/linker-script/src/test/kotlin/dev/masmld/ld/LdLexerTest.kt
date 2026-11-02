package dev.masmld.ld

import com.intellij.lexer.Lexer
import dev.masmld.ld.lexer.LdLexer
import dev.masmld.ld.lexer.LdHighlightingLexer
import dev.masmld.ld.lexer.LdTokens as T
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LdLexerTest {
    private data class Token(val type: String, val text: String, val start: Int, val end: Int, val state: Int)
    private fun scan(source: String, lexer: Lexer = LdLexer(), offset: Int = 0, state: Int = 0): List<Token> {
        lexer.start(source, offset, source.length, state)
        val tokens = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            assertTrue(lexer.tokenEnd > lexer.tokenStart)
            tokens += Token(lexer.tokenType.toString(), source.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.tokenEnd, lexer.state)
            lexer.advance()
        }
        return tokens
    }
    @Test fun `rcore source is preserved and recognizes discard counter and constants`() {
        val source = javaClass.getResource("/fixtures/rcore-linker.ld")!!.readText()
        val tokens = scan(source)
        assertEquals(source, tokens.joinToString("") { it.text })
        assertTrue(tokens.any { it.text == "/DISCARD/" && it.type == T.DISCARD.toString() })
        assertTrue(tokens.any { it.text == "." && it.type == T.DOT.toString() })
        assertTrue(tokens.any { it.text == "4K" && it.type == T.NUMBER.toString() })
        assertTrue(tokens.any { it.text == "0x80200000" && it.type == T.NUMBER.toString() })
    }
    @Test fun `GNU symbol hyphens differ from separated subtraction`() {
        val tokens = scan("A-B = A - B;")
        assertEquals(T.IDENTIFIER.toString(), tokens.first().type)
        assertEquals("A-B", tokens.first().text)
        assertEquals(1, tokens.count { it.text == "-" && it.type == T.OPERATOR.toString() })
    }
    @Test fun `comments and quoted names are opaque to highlighting`() {
        val tokens = scan("/* MEMORY { ALIGN(4K) } */\n\"ENTRY\" = 1; #include \"layout.h\"\n", LdHighlightingLexer())
        assertEquals(0, tokens.count { it.type == T.COMMAND.toString() || it.type == T.FUNCTION.toString() })
        assertTrue(tokens.any { it.type == T.COMMENT.toString() })
        assertTrue(tokens.any { it.type == T.HASH_LINE.toString() })
        assertTrue(tokens.any { it.text == "\"ENTRY\"" && it.type == T.STRING.toString() })
    }
    @Test fun `constants with GNU bases and scale suffixes stay intact`() {
        val tokens = scan("128M 4K 0x80000000 10000o 1111b 17d 0FFh 010")
        assertEquals(listOf("128M", "4K", "0x80000000", "10000o", "1111b", "17d", "0FFh", "010"), tokens.filter { it.type == T.NUMBER.toString() }.map { it.text })
    }
    @Test fun `strings recover at newline and compound operators preserve source`() {
        val tokens = scan("ASSERT(1, \"unfinished\nSECTIONS { . += 4K; x = 1 << 3; }")
        assertEquals("\"unfinished", tokens.single { it.type == T.STRING.toString() }.text)
        assertTrue(tokens.any { it.text == "SECTIONS" })
        assertTrue(tokens.any { it.text == "+=" })
        assertTrue(tokens.any { it.text == "<<" })
    }
    @Test fun `raw and highlighting lexers restart consistently at every token`() {
        val source = "MEMORY\r\n/* c */ { RAM(rx): ORIGIN = 0x8000, LENGTH=4M }\nSECTIONS { .text : { *(.text.*) } . = ALIGN(4K); /DISCARD/ : { *(.note) } }\n/* open"
        for (highlight in listOf(false, true)) {
            fun lexer(): Lexer = if (highlight) LdHighlightingLexer() else LdLexer()
            val tokens = scan(source, lexer())
            for ((index, token) in tokens.withIndex()) {
                assertEquals("restart at ${token.start}", tokens.drop(index), scan(source, lexer(), token.start, token.state))
            }
        }
    }
    @Test fun `block comment state continues a truncated buffer`() {
        val source = "/* long comment */ ENTRY(start)"
        val lexer = LdLexer()
        lexer.start(source, 0, 8, 0)
        lexer.advance()
        assertEquals("comment */", scan(source, LdLexer(), 8, lexer.state).first().text)
        assertEquals(T.COMMENT.toString(), scan(source, LdLexer(), 8, lexer.state).first().type)
    }
    @Test fun `random unfinished input terminates without dropping text`() {
        val chars = "abAB12_.$- /*#\"\n\t{}():;+=<>?&|\u0000"
        val random = Random(982)
        repeat(300) {
            val source = (0 until random.nextInt(0, 200)).map { chars[random.nextInt(chars.length)] }.joinToString("")
            assertEquals(source, scan(source).joinToString("") { it.text })
        }
    }
}
