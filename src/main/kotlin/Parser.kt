import TokenType.*

class Parser(private val tokens: List<Token>) {
    class ParseError(
        token: Token,
        message: String = "",
        where: String = "",
    ) : Exception("[line ${token.pos}$where] $message, got $token")

    private var current = 0

    private val curToken get() = tokens[current]
    private val isAtEnd get() = curToken.type == EOF
    private val prevToken get() = tokens[current - 1]

    fun parse() = program()

    private fun program(): Program {
        val stmts = ArrayList<Stmt>()
        while (!isAtEnd)
            stmts.add(declaration())
        return Program(stmts)
    }

    private fun declaration(): Stmt = when {
        match(LET) -> variableDecl()
        match(FUN) -> functionDef()
        else -> statement()
    }

    private fun variableDecl(): Stmt.VariableDecl {
        val name = ident(" after keyword 'let'")
        val init = if (match(EQUAL)) expression() else null
        consume(SEMICOLON, "expected ';' at end of let declaration")
        return Stmt.VariableDecl(name, init)
    }

    private fun functionDef(): Stmt.FunctionDef {
        val name = ident(" after keyword 'fun'")
        consume(LEFT_PAREN, "expected '(' after function name")
        val parameters = parenCommaList(::ident, "parameter")
        consume(LEFT_BRACE, "expected '{' after function signature")
        val body = block()
        return Stmt.FunctionDef(name, parameters, body)
    }

    private fun statement(): Stmt {
        val blockStmt = when {
            match(LEFT_BRACE) -> block()
            match(IF) -> ifStmt()
            match(FOR) -> forStmt()
            else -> null
        }
        if (blockStmt != null)
            return blockStmt
        val lineStmt = when {
            match(RETURN) -> Stmt.Return(expression())
            match(BREAK) -> Stmt.Break()
            else -> null
        }
        if (lineStmt != null) {
            consume(SEMICOLON, "expected ';' at end of statement")
            return lineStmt
        }
        return exprStmt()
    }

    private fun forStmt(): Stmt.For {
        consume(LEFT_PAREN, "expected '(' after 'for'")
        val init = when {
            match(SEMICOLON) -> null
            match(LET) -> variableDecl()
            else -> {
                val expr = exprStmt()
                if (expr.emitValue)
                    throw error("expected ';' after initializer expr")
                expr
            }
        }
        val condition = when {
            check(SEMICOLON) -> null
            else -> expression()
        }
        consume(SEMICOLON, "expected ';' after condition")
        val update = when {
            check(RIGHT_PAREN) -> null
            else -> expression()
        }
        consume(RIGHT_PAREN, "expected ')' after for clauses")
        val body = statement()
        return Stmt.For(init, condition, update, body)
    }

    private fun ifStmt(): Stmt.If {
        consume(LEFT_PAREN, "expected '(' after 'if'")
        val condition = expression()
        consume(RIGHT_PAREN, "expected ')' at end of condition")
        val ifBody = statement()
        var elseBody: Stmt? = null
        if (match(ELSE)) {
            elseBody = statement()
        }
        return Stmt.If(condition, ifBody, elseBody)
    }

    private fun block(): Stmt.Block {
        val stmts = ArrayList<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd)
            stmts.add(declaration())
        consume(RIGHT_BRACE, "expected '}' after block")
        return Stmt.Block(stmts)
    }

    private fun exprStmt() =
        Stmt.ExprStmt(expression(), emitValue = !match(SEMICOLON))


    private fun expression() =
        assignment()

    private fun assignment(): Expr {
        val expr = or()

        if (match(TokenType.Assignment)) {
            val operator = prevToken
            val value = assignment()
            if (expr !is Expr.Variable)
                throw error("expected variable on lhs of '$operator'")
            return Expr.Assignment(expr.target, operator, value)
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

    private fun or() =
        parseLeftAssoc(::and, OR)

    private fun and() =
        parseLeftAssoc(::equality, AND)

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
            val arguments = parenCommaList(::expression, "argument")
            return Expr.Call(expr, arguments)
        }

        return expr
    }

    private fun <T> parenCommaList(paramFn: () -> T, kind: String): ArrayList<T> {
        val list = ArrayList<T>()
        while (!match(RIGHT_PAREN)) {
            val item = paramFn()
            list.add(item)
            if (!check(RIGHT_PAREN))
                consume(COMMA, "expected ',' after $kind")
        }
        return list
    }

    private fun primary() = when {
        match(FALSE) -> Expr.Literal(false)
        match(TRUE) -> Expr.Literal(true)
        match(NIL) -> Expr.Literal(null)
        match(NUMBER, STRING) -> Expr.Literal(prevToken.literal)
        match(ATOM) -> Expr.Literal(Atom(prevToken.literal as String))
        match(LEFT_PAREN) -> {
            val expression = expression()
            consume(RIGHT_PAREN, "parentheses not balanced")
            Expr.Grouping(expression)
        }
        check(IDENTIFIER) -> Expr.Variable(ident())
        else -> throw error("expected primary expression")
    }

    private fun ident(where: String? = null) =
        if (match(IDENTIFIER)) Ident(prevToken.lexeme)
        else throw error("expected identifier$where")


    private fun check(type: TokenType) =
        !isAtEnd && curToken.type == type

    private fun advance(): Token {
        if (!isAtEnd) current++
        return prevToken
    }

    private fun match(vararg types: TokenType): Boolean = match(types.toSet())

    private fun match(types: Set<TokenType>): Boolean = when (curToken.type) {
        in types -> {
            current++
            true
        }
        else -> false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(message)
    }

    private fun error(message: String): ParseError {
        val where = if (curToken.type == EOF) " at end" else " at '${curToken.lexeme}'"
        return ParseError(curToken, message, where)
    }
}
