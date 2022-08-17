import TokenType.*

class Parser(private val tokens: List<Token>) {
    class ParseError(
        token: Token,
        message: String = "",
        where: String = "",
    ) : Exception("[pos ${token.pos}$where] $message, got '${token.lexeme}'")

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
        matchAndConsume(FUN) -> functionDef()
        else -> statement()
    }

    private fun variableDecl(): Stmt.VariableDecl {
        val name = ident(" to start variable declaration")
        val init = if (matchAndConsume(EQUAL)) expression() else null
        consume(SEMICOLON, " after variable declaration")
        return Stmt.VariableDecl(name, init)
    }

    private fun functionDef(): Stmt.FunctionDef {
        val name = ident(" after keyword 'fun'")
        consume(LEFT_PAREN, " after function name")
        val parameters = commaList(::ident, RIGHT_PAREN, "param list")
        consume(LEFT_CURLY, " after function signature")
        val body = block()
        return Stmt.FunctionDef(name, parameters, body)
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
        val body = exprStmt()
        return MatchClause(pattern, body)
    }

    private fun pattern(): MatchPattern = when {
        check(TokenType.Literals) ->
            MatchPattern.Literal(literal())
        check(IDENTIFIER) ->
            MatchPattern.Anything(ident())
        matchAndConsume(ELSE) ->
            MatchPattern.Anything(null)
        else ->
            throw error("illegal match pattern")
    }

    private fun forInStmt(): Stmt.ForIn {
        val iterator = when {
            check(IDENTIFIER) -> variable()
            check(LEFT_PAREN) -> fullTuple()
            else -> throw error("iterator must either be single variable or tuple")
        }
        consume(IN, " after for..in iterator")
        val iteratee = expression()
        consume(LEFT_CURLY, " after for..in initializer")
        val body = block()
        return Stmt.ForIn(iterator, iteratee, body)
    }


    private fun whileStmt(): Stmt.While {
        val condition = expression()
        consume(LEFT_CURLY, " after while condition")
        val body = block()
        return Stmt.While(condition, body)
    }

    private fun ifExpr(): Expr.If {
        val condition = expression()
        consume(LEFT_CURLY, " after if-condition")
        val ifBody = block()
        var elseBody: Expr.Block? = null
        if (matchAndConsume(ELSE)) {
            consume(LEFT_CURLY, " after else")
            elseBody = block()
        }
        return Expr.If(condition, ifBody, elseBody)
    }

    private fun block(): Expr.Block {
        val stmts = ArrayList<Stmt>()
        while (!check(RIGHT_CURLY) && !isAtEnd)
            stmts.add(declaration())
        consume(RIGHT_CURLY, " after block")
        return Expr.Block(stmts)
    }

    private fun exprStmt() =
        Stmt.ExprStmt(
            expression(),
            emitValue = !matchAndConsume(SEMICOLON)
        )


    private fun expression() = when {
        matchAndConsume(LEFT_CURLY) -> block()
        matchAndConsume(IF) -> ifExpr()
        matchAndConsume(MATCH) -> matchExpr()
        else -> assignment()
    }

    private fun assignment(): Expr {
        val target = or()

        if (matchAndConsume(TokenType.Assignment)) {
            val operator = prevToken
            val value = assignment()
            if (target !is Expr.Variable && target !is Expr.Access)
                throw error("only variable and member access are allowed as LHS for assignment")
            return Expr.Assignment(target, operator, value)
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
        val expr = access()

        if (matchAndConsume(LEFT_PAREN)) {
            val arguments = commaList(::expression, RIGHT_PAREN, "arg list")
            return Expr.Call(expr, arguments)
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
        else -> throw error("expected literal")
    }

    private fun collectionLiteral(): Expr.Literal = when {
        check(IDENTIFIER) && check(COLON, offset = 1) -> {
            val entryList = commaList(::dictionaryEntry, RIGHT_SQUARE, "dictionary")
            Expr.Literal(hashMapOf(*entryList.toTypedArray()))
        }
        matchAndConsume(COLON) && matchAndConsume(RIGHT_SQUARE) ->
            Expr.Literal(hashMapOf<String, Expr>())
        else ->
            Expr.Literal(commaList(::expression, RIGHT_SQUARE, "array"))
    }

    private fun dictionaryEntry(): Pair<String, Expr> {
        val key = ident(" for dictionary key")
        consume(COLON, " after dictionary key")
        val value = expression()
        return Pair(key.name, value)
    }

    private fun primary() = when {
        check(TokenType.Literals) -> literal()
        check(IDENTIFIER) -> variable()
        matchAndConsume(LEFT_PAREN) -> parenExpr()
        else -> throw error("expected primary expression")
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
        else throw error("expected identifier$where")

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
        throw error("expected '${type.match}'$where")
    }

    private fun error(message: String): ParseError {
        val where = if (curToken.type == EOF) " at end" else " at '${curToken.lexeme}'"
        return ParseError(curToken, message, where)
    }
}
