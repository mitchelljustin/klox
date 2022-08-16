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
        val name = ident(" to start variable declaration")
        val init = if (match(EQUAL)) expression() else null
        consume(SEMICOLON, "expected ';' at end of let declaration")
        return Stmt.VariableDecl(name, init)
    }

    private fun functionDef(): Stmt.FunctionDef {
        val name = ident(" after keyword 'fun'")
        consume(LEFT_PAREN, "expected '(' after function name")
        val parameters = commaList(::ident, "parameter", RIGHT_PAREN)
        consume(LEFT_CURLY, "expected '{' after function signature")
        val body = block()
        return Stmt.FunctionDef(name, parameters, body)
    }

    private fun statement(): Stmt {
        val blockStmt = when {
            match(LEFT_CURLY) -> block()
            match(IF) -> ifStmt()
            match(FOR) -> forStmt()
            else -> null
        }
        if (blockStmt != null)
            return blockStmt
        val lineStmt = when {
            match(RETURN) -> Stmt.Return(if (check(SEMICOLON)) null else expression())
            match(BREAK) -> Stmt.Break()
            else -> null
        }
        if (lineStmt != null) {
            consume(SEMICOLON, "expected ';' at end of statement")
            return lineStmt
        }
        return exprStmt()
    }

    private fun forStmt(): Stmt {
        if (check(IDENTIFIER) && check(IN, offset = 1))
            return forInStmt()
        val init = when {
            check(LEFT_CURLY) -> null
            match(SEMICOLON) -> null
            match(LET) -> variableDecl()
            else -> exprStmt(expectSemicolon = "expected ';' after for init clause")
        }
        val condition = when {
            check(LEFT_CURLY) -> null
            match(SEMICOLON) -> null
            else -> exprStmt(expectSemicolon = "expected ';' after for condition clause").expr
        }
        val update = when {
            check(LEFT_CURLY) -> null
            else -> expression()
        }
        consume(LEFT_CURLY, "expected '{' after for clauses")
        val body = block()
        return Stmt.For(init, condition, update, body)
    }

    private fun forInStmt(): Stmt.ForIn {
        val variable = ident()
        consume(IN)
        val iterable = expression()
        consume(LEFT_CURLY, "expected '{' after for..in loop")
        val body = block()
        return Stmt.ForIn(variable, iterable, body)
    }

    private fun ifStmt(): Stmt.If {
        val condition = expression()
        consume(LEFT_CURLY, "expected '{' after if-condition")
        val ifBody = block()
        var elseBody: Stmt.Block? = null
        if (match(ELSE)) {
            consume(LEFT_CURLY, "expected '{' after else")
            elseBody = block()
        }
        return Stmt.If(condition, ifBody, elseBody)
    }

    private fun block(): Stmt.Block {
        val stmts = ArrayList<Stmt>()
        while (!check(RIGHT_CURLY) && !isAtEnd)
            stmts.add(declaration())
        consume(RIGHT_CURLY, "expected '}' after block")
        return Stmt.Block(stmts)
    }

    private fun exprStmt(expectSemicolon: String? = null) =
        Stmt.ExprStmt(
            expression(), emitValue = when (expectSemicolon) {
                null -> !match(SEMICOLON)
                else -> {
                    consume(SEMICOLON, expectSemicolon)
                    false
                }
            }
        )


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
        val expr = access()

        if (match(LEFT_PAREN)) {
            val arguments = commaList(::expression, "argument", RIGHT_PAREN)
            return Expr.Call(expr, arguments)
        }

        return expr
    }

    private fun access(): Expr {
        var expr = primary()

        while (match(DOT)) {
            val field = ident()
            expr = Expr.Access(expr, field)
        }

        return expr
    }

    private fun <T> commaList(itemFunc: () -> T, kind: String, ender: TokenType): ArrayList<T> {
        val list = ArrayList<T>()
        while (!match(ender)) {
            val item = itemFunc()
            list.add(item)
            if (!check(ender))
                consume(COMMA, "expected ',' after $kind")
        }
        if (list.isNotEmpty())
            match(COMMA) // optional trailing comma
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
        match(LEFT_SQUARE) -> Expr.Literal(commaList(::expression, "array item", RIGHT_SQUARE))
        check(IDENTIFIER) -> Expr.Variable(ident())
        else -> throw error("expected primary expression")
    }

    private fun ident(where: String = "") =
        if (match(IDENTIFIER)) Ident(prevToken.lexeme)
        else throw error("expected identifier$where")


    private fun check(type: TokenType, offset: Int = 0) =
        !isAtEnd && tokens.getOrNull(current + offset)?.type == type

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

    private fun consume(type: TokenType, message: String? = null): Token {
        if (check(type)) return advance()
        throw error(message ?: "")
    }

    private fun error(message: String): ParseError {
        val where = if (curToken.type == EOF) " at end" else " at '${curToken.lexeme}'"
        return ParseError(curToken, message, where)
    }
}
