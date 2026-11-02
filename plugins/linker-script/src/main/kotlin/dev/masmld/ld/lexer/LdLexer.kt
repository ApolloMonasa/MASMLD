package dev.masmld.ld.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import dev.masmld.ld.lexer.LdTokens as T

/** Lossless, restartable lexer. Comments and strings never expose keyword tokens. */
class LdLexer : LexerBase() {
    private var text: CharSequence = ""
    private var end = 0
    private var from = 0
    private var to = 0
    private var state = 0
    private var afterState = 0
    private var type: IElementType? = null
    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        text = buffer; end = endOffset; from = startOffset; state = initialState
        scan()
    }
    override fun getState() = state
    override fun getTokenType() = type
    override fun getTokenStart() = from
    override fun getTokenEnd() = to
    override fun getBufferSequence() = text
    override fun getBufferEnd() = end
    override fun advance() { from = to; state = afterState; scan() }
    private fun at(i: Int) = if (i < end) text[i] else '\u0000'
    private fun nameStart(c: Char) = c.isLetter() || c in "_.$"
    private fun namePart(c: Char) = nameStart(c) || c.isDigit() || c == '-'
    private fun scan() {
        to = from; afterState = 0
        if (from >= end) { type = null; return }
        val c = at(from)
        if (state == IN_COMMENT || (c == '/' && at(from + 1) == '*')) {
            to = if (state == IN_COMMENT) from else from + 2
            while (to < end && !(at(to) == '*' && at(to + 1) == '/')) to++
            if (to < end) to += 2 else afterState = IN_COMMENT
            type = T.COMMENT
            return
        }
        if (c == '/' && from + 9 <= end && text.subSequence(from, from + 9).toString() == "/DISCARD/") {
            to = from + 9; type = T.DISCARD; return
        }
        to++
        type = when {
            c == '\n' || c == '\r' -> { if (c == '\r' && at(to) == '\n') to++; T.NEWLINE }
            c == ' ' || c == '\t' || c == '\u000c' -> {
                while (to < end && at(to) in " \t\u000c") to++
                TokenType.WHITE_SPACE
            }
            c == '#' -> {
                while (to < end && at(to) !in "\r\n") to++
                T.HASH_LINE
            }
            c == '"' -> {
                while (to < end && at(to) !in "\r\n") {
                    if (at(to) == '"') { to++; break }
                    to++
                }
                T.STRING
            }
            nameStart(c) -> {
                while (to < end && namePart(at(to))) to++
                if (to == from + 1 && c == '.') T.DOT else T.IDENTIFIER
            }
            c.isDigit() -> {
                // Base and K/M suffixes remain part of the literal; no numeric evaluation here.
                while (to < end && at(to).isLetterOrDigit()) to++
                T.NUMBER
            }
            c == '{' -> T.LBRACE
            c == '}' -> T.RBRACE
            c == '(' -> T.LPAREN
            c == ')' -> T.RPAREN
            c == ';' -> T.SEMICOLON
            c == ',' -> T.COMMA
            c == ':' -> T.COLON
            c in "+-*/%=<>!&|^~?[]" -> {
                if (to < end && text.subSequence(from, to + 1).toString() in compoundOperators) to++
                T.OPERATOR
            }
            else -> TokenType.BAD_CHARACTER
        }
    }
    companion object {
        private const val IN_COMMENT = 1
        private val compoundOperators = setOf("<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "+=", "-=", "*=", "/=", "&=", "|=")
    }
}
