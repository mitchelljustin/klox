import TokenType.*

class Parser(private val tokens: List<Token>) {
    class ParseError(
        token: Token,
        message: String = "",
    ) : Exception(
        "[pos ${token.pos} ${if (token.type == EOF) "at end" else "at '${token.lexeme}'"}] $message"
    )

    private var current = 0

    private val curToken get() = tokens[current]
    private val isAtEnd get() = curToken.type == EOF
    private val prevToken get() = tokens[current - 1]

    fun parse() = program()

    private fun program(): Program {
        val stmts = ArrayList<Stmt>()
        while (!isAtEnd) {
            if (matchAndConsume(SEMICOLON))
                continue
            stmts.add(declaration())
        }
        return Program(stmts)
    }

    private fun declaration(): Stmt = when {
        matchAndConsume(LET) -> variableDecl()
        matchAndConsume(FUN) -> functionDecl()
        else -> statement()
    }

    private fun variableDecl(): Stmt.VariableDecl {
        val name = ident(" to start variable declaration")
        val init = if (matchAndConsume(EQUAL)) expression() else null
        consume(SEMICOLON, " after variable declaration")
        return Stmt.VariableDecl(name, init)
    }

    private fun functionDecl(): Stmt.FunctionDecl {
        val name = ident(" for name in function declaration")
        val parameters = fullParamList()
        val body = fullBlock(" after function signature")
        return Stmt.FunctionDecl(FunctionDef(name, parameters, body))
    }

    private fun fullParamList(): ArrayList<Ident> {
        consume(LEFT_PAREN, " to start param list")
        return commaList(::ident, RIGHT_PAREN, "param list")
    }

    private fun statement(): Stmt {
        val blockStmt = when {
            matchAndConsume(FOR) -> forInStmt()
            matchAndConsume(WHILE) -> whileStmt()
            else -> null
        }
        if (blockStmt != null)
            return blockStmt
        val lineStmt = when {
            matchAndConsume(RETURN) -> Stmt.Return(if (check(SEMICOLON)) null else expression())
            matchAndConsume(BREAK) -> Stmt.Break()
            else -> null
        }
        if (lineStmt != null) {
            consume(SEMICOLON, " at end of statement")
            return lineStmt
        }
        return exprStmt()
    }

    private fun matchExpr(): Expr.Match {
        val expr = expression()
        consume(LEFT_CURLY, " after match expression")
        val clauses = commaList(::matchClause, RIGHT_CURLY, "match statement")
        return Expr.Match(expr, clauses)
    }

    private fun matchClause(): MatchClause {
        val pattern = pattern()
        consume(RIGHT_ARROW, " after match pattern")
        val body = exprStmt(forceEmitValue = "cannot end match clause body with a semicolon")
        return MatchClause(pattern, body)
    }

    private fun pattern(): MatchPattern = when {
        matchAndConsume(LEFT_SQUARE) -> when {
            isDictStart() ->
                MatchPattern.Dict(
                    commaList(::dictPatternEntry, RIGHT_SQUARE, "dict pattern")
                )
            else ->
                MatchPattern.List(
                    commaList(::pattern, RIGHT_SQUARE, "list pattern")
                )
        }
        check(IDENTIFIER) ->
            MatchPattern.Anything(ident())
        matchAndConsume(ELSE) ->
            MatchPattern.Anything(null)
        check(TokenType.Literals) ->
            MatchPattern.Literal(literal())
        else ->
            throw parseError("illegal match pattern")
    }

    private fun dictPatternEntry(): Pair<String, MatchPattern> {
        val key = ident(" for dict pattern key")
        consume(COLON, " after dict pattern key")
        val value =
            if (check(COMMA) || check(RIGHT_SQUARE))
                MatchPattern.Anything(key)
            else
                pattern()
        return Pair(key.name, value)
    }

    private fun forInStmt(): Stmt.ForIn {
        val iterator = when {
            check(IDENTIFIER) -> variable()
            check(LEFT_PAREN) -> fullTuple()
            else -> throw parseError("iterator must either be single variable or tuple")
        }
        consume(IN, " after for..in iterator")
        val iteratee = expression()
        val body = fullBlock(" after for..in initializer")
        return Stmt.ForIn(iterator, iteratee, body)
    }


    private fun whileStmt(): Stmt.While {
        val condition = expression()
        val body = fullBlock(" after while condition")
        return Stmt.While(condition, body)
    }

    private fun ifExpr(): Expr.If {
        val condition = expression()
        val ifBody = fullBlock(" after if-condition")
        val elseBody = if (matchAndConsume(ELSE)) fullBlock(" after else") else null
        return Expr.If(condition, ifBody, elseBody)
    }

    private fun fullBlock(where: String): Expr.Block {
        consume(LEFT_CURLY, where)
        val body = block()
        return body
    }

    private fun block(): Expr.Block {
        val stmts = ArrayList<Stmt>()
        while (!check(RIGHT_CURLY) && !isAtEnd)
            stmts.add(declaration())
        consume(RIGHT_CURLY, " after block")
        return Expr.Block(stmts)
    }

    private fun exprStmt(forceEmitValue: String? = null) =
        Stmt.ExprStmt(
            expression(),
            emitValue = when (forceEmitValue) {
                null -> !matchAndConsume(SEMICOLON)
                else -> {
                    if (matchAndConsume(SEMICOLON))
                        throw parseError(forceEmitValue)
                    true
                }
            }
        )


    private fun expression() = when {
        matchAndConsume(LEFT_CURLY) -> block()
        matchAndConsume(IF) -> ifExpr()
        matchAndConsume(MATCH) -> matchExpr()
        matchAndConsume(FUN) -> functionExpr()
        else -> assignment()
    }

    private fun functionExpr(): Expr {
        val name = if (check(IDENTIFIER)) ident() else null
        val parameters = fullParamList()
        val body = fullBlock(" after function expression signature")
        return Expr.Function(FunctionDef(name, parameters, body))
    }

    private fun assignment(): Expr {
        val target = or()

        if (matchAndConsume(TokenType.Assignment)) {
            val operator = prevToken
            val value = assignment()
            when (target) {
                is Expr.Variable,
                is Expr.Access,
                is Expr.Index,
                ->
                    return Expr.Assignment(target, operator, value)
                else ->
                    throw parseError("illegal LHS for assignment: ${target::class.simpleName}")
            }
        }

        return target
    }

    private fun binaryLeftAssoc(nextRule: () -> Expr, vararg tokenTypes: TokenType): Expr {
        var expr = nextRule()

        while (matchAndConsume(*tokenTypes)) {
            val operator = prevToken
            val right = nextRule()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun or() =
        binaryLeftAssoc(::and, OR)

    private fun and() =
        binaryLeftAssoc(::equality, AND)

    private fun equality() =
        binaryLeftAssoc(::comparison, EQUAL_EQUAL, BANG_EQUAL)

    private fun comparison() =
        binaryLeftAssoc(::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)

    private fun term() =
        binaryLeftAssoc(::factor, MINUS, PLUS)

    private fun factor() =
        binaryLeftAssoc(::unary, SLASH, STAR)

    private fun unary(): Expr {
        if (matchAndConsume(BANG, MINUS)) {
            val operator = prevToken
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return range()
    }

    private fun range(): Expr {
        val start = call()

        if (matchAndConsume(DOT_DOT)) {
            val end = call()
            return Expr.Range(start, end)
        }

        return start
    }

    private fun call(): Expr {
        val expr = index()

        if (matchAndConsume(LEFT_PAREN)) {
            val arguments = commaList(::expression, RIGHT_PAREN, "arg list")
            return Expr.Call(expr, arguments)
        }

        return expr
    }

    private fun index(): Expr {
        val expr = access()

        if (matchAndConsume(LEFT_SQUARE)) {
            val index = expression()
            consume(RIGHT_SQUARE, " after object index")
            return Expr.Index(expr, index)
        }

        return expr
    }

    private fun access(): Expr {
        var expr = primary()

        while (matchAndConsume(DOT)) {
            val field = ident()
            expr = Expr.Access(expr, field)
        }

        return expr
    }

    private fun <T> commaList(
        itemFunc: () -> T,
        ender: TokenType,
        listName: String,
    ): ArrayList<T> {
        val items = ArrayList<T>()
        if (matchAndConsume(ender))
            return items
        while (true) {
            items.add(itemFunc())
            if (matchAndConsume(COMMA)) {
                if (matchAndConsume(ender)) break
            } else {
                consume(ender, " at end of $listName")
                break
            }
        }
        return items
    }

    private fun literal(): Expr.Literal = when {
        matchAndConsume(FALSE) ->
            Expr.Literal(false)
        matchAndConsume(TRUE) ->
            Expr.Literal(true)
        matchAndConsume(NIL) ->
            Expr.Literal(null)
        matchAndConsume(STRING, NUMBER) ->
            Expr.Literal(prevToken.literal)
        matchAndConsume(ATOM) ->
            Expr.Literal(Atom(prevToken.literal as String))
        matchAndConsume(LEFT_SQUARE) ->
            collectionLiteral()
        else -> throw parseError("expected literal")
    }

    private fun collectionLiteral(): Expr.Literal = when {
        isDictStart() -> {
            val entryList = commaList(::dictEntry, RIGHT_SQUARE, "dict")
            Expr.Literal(hashMapOf(*entryList.toTypedArray()))
        }
        matchAndConsume(COLON) && matchAndConsume(RIGHT_SQUARE) ->
            Expr.Literal(hashMapOf<String, Expr>())
        else ->
            Expr.Literal(commaList(::expression, RIGHT_SQUARE, "array"))
    }

    private fun isDictStart() = check(IDENTIFIER) && check(COLON, offset = 1)

    private fun dictEntry(): Pair<String, Expr> {
        val key = ident(" for dict key")
        consume(COLON, " after dict key")
        val value = expression()
        return Pair(key.name, value)
    }

    private fun primary() = when {
        check(TokenType.Literals) -> literal()
        check(IDENTIFIER) -> variable()
        matchAndConsume(LEFT_PAREN) -> parenExpr()
        else -> throw parseError("expected primary expression")
    }

    private fun parenExpr(): Expr {
        if (matchAndConsume(RIGHT_PAREN))
            return Expr.Tuple(listOf())
        val expression = expression()
        return when {
            check(COMMA) -> tuple(expression)
            else -> {
                consume(RIGHT_PAREN, " after grouping expression")
                Expr.Grouping(expression)
            }
        }
    }

    private fun variable() = Expr.Variable(ident())

    private fun ident(where: String = "") =
        if (matchAndConsume(IDENTIFIER)) Ident(prevToken.lexeme)
        else throw parseError("expected identifier$where")

    private fun fullTuple(): Expr.Tuple {
        consume(LEFT_PAREN, " at tuple start")
        return tuple(expression())
    }

    private fun tuple(firstItem: Expr): Expr.Tuple {
        consume(COMMA, " after first item in tuple")
        val elements = listOf(firstItem) + commaList(
            ::expression,
            ender = RIGHT_PAREN,
            listName = "tuple",
        )
        return Expr.Tuple(elements)
    }

    private fun check(type: TokenType, offset: Int = 0) = check(setOf(type), offset)

    private fun check(types: Set<TokenType>, offset: Int = 0) =
        !isAtEnd && tokens.getOrNull(current + offset)?.type in types

    private fun advance(): Token {
        if (!isAtEnd) increment()
        return prevToken
    }

    private fun matchAndConsume(vararg types: TokenType): Boolean = matchAndConsume(types.toSet())

    private fun matchAndConsume(types: Set<TokenType>): Boolean = when (curToken.type) {
        in types -> {
            increment()
            true
        }
        else -> false
    }

    private fun increment() {
        current++
    }

    private fun consume(type: TokenType, where: String = ""): Token {
        if (check(type)) return advance()
        throw parseError("expected '${type.match}'$where")
    }

    private fun parseError(message: String) = ParseError(curToken, message)
}
