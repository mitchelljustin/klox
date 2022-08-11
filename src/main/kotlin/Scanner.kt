import TokenType.*

class Scanner(val source: String) {
    private var tokens = ArrayList<Token>()
    private var start = 0
    private var current = 0
    private var line = 0

    private val curChar: Char? get() = source.getOrNull(current)
    private val nextChar: Char? get() = source.getOrNull(current + 1)

    private val DIGITS = '0'..'9'
    private val ALPHA = listOf('a'..'z', 'A'..'Z', '_'..'_').flatten()
    private val ALPHANUM = listOf(DIGITS, ALPHA).flatten()

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }
        tokens.add(Token(EOF, "", line))
        return tokens
    }

    private fun scanToken() {
        when (advance()) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '{' -> addToken(LEFT_BRACE)
            '}' -> addToken(RIGHT_BRACE)
            ',' -> addToken(COMMA)
            '.' -> addToken(DOT)
            '-' -> addToken(MINUS)
            '+' -> addToken(PLUS)
            ';' -> addToken(SEMICOLON)
            '*' -> addToken(STAR)
            '!' -> addToken(if (match('=')) BANG_EQUAL else BANG)
            '=' -> addToken(if (match('=')) EQUAL_EQUAL else EQUAL)
            '<' -> addToken(if (match('=')) LESS_EQUAL else LESS)
            '>' -> addToken(if (match('=')) GREATER_EQUAL else GREATER)
            '/' -> {
                if (match('/')) {
                    while (curChar != '\n' && !isAtEnd()) advance()
                } else {
                    addToken(SLASH)
                }
            }
            ' ', '\r', '\t' -> {}
            '\n' -> line++
            '"' -> string()
            in DIGITS -> number()
            in ALPHA -> identifier()
            else -> {
                Lox.error(line, "Unexpected character.")
            }
        }
    }

    private fun identifier() {
        while (curChar in ALPHANUM) advance()

        val type = when (source.slice(start until current)) {
            "and" -> AND
            "class" -> CLASS
            "else" -> ELSE
            "false" -> FALSE
            "for" -> FOR
            "fun" -> FUN
            "if" -> IF
            "nil" -> NIL
            "or" -> OR
            "print" -> PRINT
            "return" -> RETURN
            "super" -> SUPER
            "this" -> THIS
            "true" -> TRUE
            "var" -> VAR
            "while" -> WHILE
            else -> IDENTIFIER
        }

        addToken(type)
    }

    private fun number() {
        while (curChar in DIGITS) advance()
        if (curChar == '.' && nextChar in DIGITS) {
            advance()
            while (curChar in DIGITS) advance()
        }
        val string = source.slice(start until current)
        addToken(NUMBER, string.toDouble())
    }

    private fun string() {
        while (curChar != '"' && !isAtEnd()) {
            if (curChar == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.")
            return
        }

        advance()

        val literal = source.slice(start + 1 until current - 1) // omit quotes
        addToken(STRING, literal)
    }

    private fun isAtEnd() = current >= source.length

    private fun advance() = source[current++]

    private fun addToken(type: TokenType, literal: Any? = null) {
        val lexeme = source.slice(start until current)
        tokens.add(Token(type, lexeme, line, literal))
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (curChar != expected) return false

        current++
        return true
    }
}