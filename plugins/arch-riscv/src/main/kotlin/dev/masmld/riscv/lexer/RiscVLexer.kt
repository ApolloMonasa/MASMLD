package dev.masmld.riscv.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.riscv.lexer.RiscVTokens as T

/** GNU-style assembly lexer. States describe the start of the current token. */
class RiscVLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var limit = 0
    private var from = 0
    private var to = 0
    private var kind: IElementType? = null
    private var state = 0
    private var nextState = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        limit = endOffset
        from = startOffset
        state = initialState
        scan()
    }
    override fun getState() = state
    override fun getTokenType() = kind
    override fun getTokenStart() = from
    override fun getTokenEnd() = to
    override fun getBufferSequence() = buffer
    override fun getBufferEnd() = limit
    override fun advance() {
        from = to
        state = nextState
        scan()
    }
    private fun at(offset: Int) = if (offset < limit) buffer[offset] else '\u0000'
    private fun nameStart(c: Char) = c.isLetter() || c in "_.$"
    private fun namePart(c: Char) = nameStart(c) || c.isDigit()
    private fun scan() {
        nextState = 0
        to = from
        if (from >= limit) { kind = null; return }
        val c = at(from)
        if (state == IN_COMMENT || (c == '/' && at(from + 1) == '*')) {
            to = if (state == IN_COMMENT) from else from + 2
            while (to < limit && !(at(to) == '*' && at(to + 1) == '/')) to++
            if (to < limit) to += 2 else nextState = IN_COMMENT
            kind = T.BLOCK_COMMENT
            return
        }
        to++
        kind = when {
            c == '\n' || c == '\r' -> {
                if (c == '\r' && at(to) == '\n') to++
                T.NEWLINE
            }
            c == ' ' || c == '\t' || c == '\u000c' -> {
                while (to < limit && at(to) in " \t\u000c") to++
                TokenType.WHITE_SPACE
            }
            c == '#' -> {
                while (to < limit && at(to) != '\n' && at(to) != '\r') to++
                T.LINE_COMMENT
            }
            c == '"' -> {
                // A missing quote recovers at the next physical line.
                while (to < limit && at(to) != '\n' && at(to) != '\r') {
                    if (at(to) == '"') { to++; break }
                    if (at(to) == '\\' && to + 1 < limit && at(to + 1) !in "\r\n") to++
                    to++
                }
                T.STRING
            }
            c == '\'' -> {
                // GAS character constants use a leading quote, without a closing quote.
                if (to < limit) {
                    if (at(to) == '\\' && to + 1 < limit) to++
                    to++
                }
                T.CHARACTER
            }
            c == '\\' -> {
                while (to < limit && (namePart(at(to)) || at(to) in "@+")) to++
                T.MACRO_PARAMETER
            }
            nameStart(c) -> {
                while (to < limit && namePart(at(to))) to++
                T.IDENTIFIER
            }
            c.isDigit() -> {
                if (c == '0' && at(to) in "xXbB" && to < limit) {
                    to++
                    while (to < limit && (at(to).isDigit() || at(to).lowercaseChar() in 'a'..'f')) to++
                    T.NUMBER
                } else {
                    while (to < limit && at(to).isDigit()) to++
                    if (at(to) in "fb" && to < limit && !namePart(at(to + 1))) {
                        to++
                        T.LOCAL_REFERENCE
                    } else T.NUMBER
                }
            }
            c == ':' -> T.COLON
            c == ',' -> T.COMMA
            c == '(' -> T.LPAREN
            c == ')' -> T.RPAREN
            c == ';' -> T.SEPARATOR
            c in "+-*/%&|^~!=<>[]" -> T.OPERATOR
            else -> TokenType.BAD_CHARACTER
        }
    }
    companion object { private const val IN_COMMENT = 1 }
}
