import TokenType.*

class Scanner(
    private val source: String,
) {
    class ScanError(
        message: String,
        char: Char?,
        pos: Pos,
    ) : Exception("['${char ?: ""}' at $pos] $message")

    private val tokens = ArrayList<Token>()
    private var start = 0
    private var current = 0
    private var line = 0

    private val isAtEnd get() = current >= source.length
    private val prevChar get() = source.getOrNull(current - 1)
    private val curChar get() = source.getOrNull(current)
    private val nextChar get() = source.getOrNull(current + 1)
    private val curPos get() = Pos(line, current)

    companion object {
        private val DIGITS = '0'..'9'
        private val ALPHA = listOf('a'..'z', 'A'..'Z', '_'..'_').flatten()
        private val ALPHANUM = listOf(DIGITS, ALPHA).flatten()
        private val TOKENS = TokenType.values()
        private val KEYWORDS = TOKENS
            .filter(TokenType::isKeyword)
            .associateBy { it.match?.substring(1) }
        private val SYMBOL_DOUBLE = TOKENS
            .filter { it.match?.length == 2 }
            .groupBy { it.first }
        private val SYMBOL_SINGLE = TOKENS
            .filter { it.match?.length == 1 }
            .associateBy { it.first }
    }

    fun scan(): List<Token> {
        tokens.clear()
        while (!isAtEnd) {
            start = current
            scanToken()
        }
        tokens.add(Token(EOF, "", curPos))
        return tokens
    }

    private fun error(message: String, previous: Boolean = false) =
        ScanError(message, if (previous) prevChar else curChar, curPos)

    private fun advance() = source[current++]

    private fun addToken(type: TokenType, literal: Any? = null) {
        val lexeme = source.slice(start until current)
        tokens.add(Token(type, lexeme, curPos, literal))
    }

    private fun match(expected: Char) = when {
        isAtEnd -> false
        curChar != expected -> false
        else -> {
            current++
            true
        }
    }

    private fun scanToken() {
        when (val char = advance()) {
            '/' -> {
                if (match('/')) {
                    while (curChar != '\n' && !isAtEnd) advance()
                } else {
                    addToken(SLASH)
                }
            }
            in SYMBOL_DOUBLE -> {
                for (tokenType in SYMBOL_DOUBLE[char]!!)
                    if (match(tokenType.second!!)) {
                        addToken(tokenType)
                        return
                    }
                addToken(SYMBOL_SINGLE[char]!!)
            }
            in SYMBOL_SINGLE ->
                addToken(SYMBOL_SINGLE[char]!!)
            in setOf(' ', '\r', '\t') -> {}
            '\n' -> line++
            '"' -> string()
            in DIGITS -> number()
            in ALPHA -> identifier()
            ':' -> atom()
            else -> throw error("unexpected char", previous = true)
        }
    }

    private fun identifier() {
        while (curChar in ALPHANUM) advance()

        val ident = source.slice(start until current)
        val type = KEYWORDS.getOrDefault(ident, IDENTIFIER)

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
        while (curChar != '"' && !isAtEnd) {
            if (curChar == '\n') line++
            advance()
        }

        if (isAtEnd) throw error("unterminated string")

        advance()

        val literal = source.slice(start + 1 until current - 1) // omit quotes
        addToken(STRING, literal)
    }

    private fun atom() {
        if (curChar !in ALPHA) throw error("first char of atom must be letter")
        while (curChar in ALPHANUM) advance()
        val literal = source.slice(start + 1 until current) // omit colon
        addToken(ATOM, literal)
    }

}