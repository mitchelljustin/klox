import TokenType.*


class Parser(private val tokens: List<Token>) {
    class ParserError(private val token: Token, override val message: String) : Error(message) {
        override fun toString() = "[Line ${token.line}] $message ($token)"
    }

    private var current = 0

    private val curToken get() = tokens[current]
    private val prevToken get() = tokens[current - 1]
    private val isAtEnd get() = curToken.type == EOF

    //    val nextToken get() = tokens.getOrNull(current + 1)


    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType) = !isAtEnd && curToken.type == type

    private fun advance(): Token {
        if (!isAtEnd) current++
        return prevToken
    }

    private fun error(message: String): ParserError {
        Lox.report(curToken.line, "at '${curToken.lexeme}'", message)
        return ParserError(curToken, message)
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(message)
    }

    private fun parseLeftAssoc(subExpr: () -> Expr, vararg tokenTypes: TokenType): Expr {
        var expr = subExpr()

        while (match(*tokenTypes)) {
            val operator = prevToken
            val right = subExpr()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun expression() = equality()
    private fun equality() = parseLeftAssoc(::comparison, EQUAL_EQUAL, BANG_EQUAL)
    private fun comparison() = parseLeftAssoc(::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)
    private fun term() = parseLeftAssoc(::factor, MINUS, PLUS)
    private fun factor() = parseLeftAssoc(::unary, SLASH, STAR)
    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = prevToken
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return primary()
    }

    private fun primary() = when {
        match(FALSE) -> Expr.Literal(false)
        match(TRUE) -> Expr.Literal(true)
        match(NIL) -> Expr.Literal(null)
        match(NUMBER, STRING) -> Expr.Literal(prevToken.literal)
        match(LEFT_PAREN) -> {
            val expression = expression()
            consume(RIGHT_PAREN, "parentheses not balanced")
            Expr.Grouping(expression)
        }
        else -> throw error("unexpected primary")
    }
}