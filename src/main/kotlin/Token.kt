enum class TokenType(val match: String? = null) {
    LEFT_PAREN("("), RIGHT_PAREN(")"), LEFT_CURLY("{"), RIGHT_CURLY("}"),
    LEFT_SQUARE("["), RIGHT_SQUARE("]"),
    COMMA(","), DOT("."), SEMICOLON(";"), COLON(":"),
    DOT_DOT(".."),
    MINUS("-"), PLUS("+"), SLASH("/"), STAR("*"),
    MINUS_EQUAL("-="), PLUS_EQUAL("+="), SLASH_EQUAL("/="), STAR_EQUAL("*="),

    // One or two character tokens.
    BANG("!"), BANG_EQUAL("!="),
    EQUAL("="), EQUAL_EQUAL("=="),
    GREATER(">"), GREATER_EQUAL(">="),
    LESS("<"), LESS_EQUAL("<="),

    // Arrow
    RIGHT_ARROW("->"),

    // Literals.
    IDENTIFIER, STRING, NUMBER, ATOM,

    // Keywords.
    AND(":and"), CLASS(":class"), ELSE(":else"), FALSE(":false"),
    FUN(":fun"), FOR(":for"), IF(":if"), NIL(":nil"), OR(":or"),
    RETURN(":return"), SUPER(":super"), THIS(":this"), TRUE(":true"),
    LET(":let"), WHILE(":while"), MATCH(":match"),
    BREAK(":break"), IN(":in"),

    EOF;

    val isKeyword: Boolean get() = match != null && match.length > 1 && match.startsWith(":")
    val first: Char? get() = match?.first()
    val second: Char? get() = match?.get(1)
    val opposing: TokenType?
        get() = when (this) {
            LEFT_PAREN -> RIGHT_PAREN
            RIGHT_PAREN -> LEFT_PAREN
            LEFT_CURLY -> RIGHT_CURLY
            RIGHT_CURLY -> LEFT_CURLY
            LEFT_SQUARE -> RIGHT_SQUARE
            RIGHT_SQUARE -> LEFT_SQUARE
            else -> null
        }
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