enum class TokenType {
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    // One or two character tokens.
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // Literals.
    IDENTIFIER, STRING, NUMBER,

    // Keywords.
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    RETURN, SUPER, THIS, TRUE, LET, WHILE,
    BREAK,

    EOF
}

data class Pos(val line: Int, val col: Int) {
    override fun toString() = "$line:$col"
}

class Token(
    val type: TokenType,
    val lexeme: String,
    val pos: Pos,
    val literal: Any? = null,
) {
    override fun toString() = "$type($lexeme)"
}