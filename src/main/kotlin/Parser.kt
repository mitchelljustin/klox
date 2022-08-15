import TokenType.*

class Parser(private val tokens: List<Token>) {
    class ParseError(
        private val token: Token,
        override val message: String? = null,
        private val where: String? = null,
    ) : kotlin.Exception(message) {
        override fun toString() = "[line ${token.line}${where ?: ""}] $message $token"
    }

    private var current = 0

    private val curToken get() = tokens[current]
    private val isAtEnd get() = curToken.type == EOF
    private val prevToken get() = tokens[current - 1]


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

    private fun error(message: String): ParseError {
        val where = if (curToken.type == EOF) " at end" else " at '${curToken.lexeme}'"
        return ParseError(curToken, message, where)
    }

    private fun consume(type: TokenType, message: String): Token {
        if (!isAtEnd && check(type)) return advance()
        throw error(message)
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd) {
            if (prevToken.type == SEMICOLON) return

            if (curToken.type in listOf(CLASS, FUN, LET, FOR, IF, WHILE, RETURN)) return

            advance()
        }
    }

    fun parse() = program()

    private fun program(): Program {
        val stmts = ArrayList<Stmt>()
        while (!isAtEnd) stmts.add(declaration())
        return Program(stmts)
    }

    private fun declaration(): Stmt = when {
        match(LET) -> {
            val name = consume(IDENTIFIER, "expected identifier after 'var'")
            val init = if (match(EQUAL)) expression() else null
            consume(SEMICOLON, "expected semicolon at end of stmt")
            Stmt.VariableDecl(Ident(name.lexeme), init)
        }
        else -> statement()
    }

    private fun statement(): Stmt {
        val stmt = when {
            match(LEFT_BRACE) -> return block()
            else -> exprStmt()
        }
        consume(SEMICOLON, "expected semicolon at end of stmt")
        return stmt
    }

    private fun block(): Stmt.Block {
        val stmts = ArrayList<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd)
            stmts.add(declaration())
        consume(RIGHT_BRACE, "expected '}' after block")
        return Stmt.Block(stmts)
    }

    private fun exprStmt() =
        Stmt.ExprStmt(expression())

    private fun expression() =
        assignment()

    private fun assignment(): Expr {
        val expr = equality()

        if (match(EQUAL)) {
            val value = assignment()
            if (expr is Expr.Variable)
                return Expr.Assignment(target = expr.variable, value)
            throw error("expected variable on lhs of equal(=)")
        }

        return expr
    }

    private fun parseLeftAssoc(nextRule: () -> Expr, vararg tokenTypes: TokenType): Expr {
        var expr = nextRule()

        while (match(*tokenTypes)) {
            val operator = prevToken
            val right = nextRule()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun equality() =
        parseLeftAssoc(::comparison, EQUAL_EQUAL, BANG_EQUAL)

    private fun comparison() =
        parseLeftAssoc(::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)

    private fun term() =
        parseLeftAssoc(::factor, MINUS, PLUS)

    private fun factor() =
        parseLeftAssoc(::unary, SLASH, STAR)

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = prevToken
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return call()
    }

    private fun call(): Expr {
        val expr = primary()

        if (match(LEFT_PAREN)) {
            val arguments = ArrayList<Expr>()
            while (!match(RIGHT_PAREN)) {
                val argument = expression()
                arguments.add(argument)
                if (!check(RIGHT_PAREN))
                    consume(COMMA, "expected ',' after argument")
            }
            return Expr.Call(expr, arguments)
        }

        return expr
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
        match(IDENTIFIER) -> Expr.Variable(Ident(prevToken.lexeme))
        else -> throw error("expected expression")
    }

}
