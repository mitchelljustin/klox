import TokenType.*

class Parser(private val tokens: List<Token>) {
    class ParseError(
        token: Token,
        message: String = "",
    ) : Exception(
        "[pos ${token.pos} ${if (token.type == EOF) "at end" else "at '${token.lexeme}'"}] $message"
    )

    companion object {
        val Literals = setOf(STRING, NUMBER, ATOM, NIL, TRUE, FALSE, LEFT_SQUARE)
    }

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
            stmts.add(statement())
        }
        return Program(stmts)
    }

    private fun variableDecl(): Stmt.VariableDecl {
        val name = ident(" to start variable declaration")
        val init = if (matchAndConsume(EQUAL)) expression() else null
        return Stmt.VariableDecl(name, init)
    }

    private fun functionDecl(): Stmt.FunctionDecl {
        val name = ident(" for name in function")
        val parameters = fullParamList()
        val body = block(" after function signature")
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
            matchAndConsume(FUN) -> functionDecl()
            else -> null
        }
        if (blockStmt != null)
            return blockStmt
        val lineStmt = when {
            matchAndConsume(LET) -> variableDecl()
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
        check(Literals) ->
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
            check(LEFT_PAREN) -> tuple()
            else -> throw parseError("iterator must either be single variable or tuple")
        }
        consume(IN, " after for..in iterator")
        val iteratee = expression()
        val body = block(" after for..in initializer")
        return Stmt.ForIn(iterator, iteratee, body)
    }


    private fun whileStmt(): Stmt.While {
        val condition = expression()
        val body = block(" after while condition")
        return Stmt.While(condition, body)
    }

    private fun ifExpr(): Expr.If {
        val condition = expression()
        val ifBody = block(" after if-condition")
        val elseBody = if (matchAndConsume(ELSE)) block(" after else") else null
        return Expr.If(condition, ifBody, elseBody)
    }

    private fun block(where: String): Expr.Block {
        consume(LEFT_CURLY, where)
        return blockPartial()
    }

    private fun blockPartial(): Expr.Block {
        val stmts = ArrayList<Stmt>()
        while (!check(RIGHT_CURLY) && !isAtEnd)
            stmts.add(statement())
        val illegalExprStmts = stmts
            .subList(0, stmts.size - 1)
            .filterIsInstance<Stmt.ExprStmt>()
            .filter { it.emitValue }
        if (illegalExprStmts.isNotEmpty())
            throw parseError("only last statement in block may omit semicolon to emit value")
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
        matchAndConsume(LEFT_CURLY) -> blockPartial()
        matchAndConsume(IF) -> ifExpr()
        matchAndConsume(MATCH) -> matchExpr()
        matchAndConsume(FUN) -> functionExpr()
        else -> assignment()
    }

    private fun functionExpr(): Expr {
        val name = if (check(IDENTIFIER)) ident() else null
        val parameters = fullParamList()
        val body = block(" after function expression signature")
        return Expr.Function(FunctionDef(name, parameters, body))
    }

    private fun assignment(): Expr {
        val target = or()

        if (matchAndConsume(EQUAL, MINUS_EQUAL, PLUS_EQUAL, SLASH_EQUAL, STAR_EQUAL)) {
            val operator = prevToken
            val value = assignment()
            return when (target) {
                is Expr.Variable,
                is Expr.Access,
                is Expr.Index,
                ->
                    Expr.Assignment(target, operator, value)
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
        var expr = primary()

        while (true)
            expr = when {
                matchAndConsume(LEFT_PAREN) -> {
                    val arguments = commaList(::expression, RIGHT_PAREN, "arg list")
                    Expr.Call(expr, arguments)
                }
                matchAndConsume(DOT) -> {
                    val member = ident()
                    Expr.Access(expr, member)
                }
                matchAndConsume(LEFT_SQUARE) -> {
                    val index = expression()
                    consume(RIGHT_SQUARE, " after object index")
                    Expr.Index(expr, index)
                }
                else -> break
            }

        return expr
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
        else -> throw parseError("illegal literal")
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
        check(Literals) -> literal()
        check(IDENTIFIER) -> variable()
        matchAndConsume(LEFT_PAREN) -> parenExpr()
        else -> throw parseError("expected primary expression")
    }

    private fun parenExpr(): Expr {
        if (matchAndConsume(RIGHT_PAREN))
            return Expr.Tuple(listOf())
        val expression = expression()
        return when {
            check(COMMA) -> tuplePartial(expression)
            else -> {
                consume(RIGHT_PAREN, " after grouping expression")
                Expr.Grouping(expression)
            }
        }
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

    private fun variable() = Expr.Variable(ident())

    private fun ident(where: String = "") =
        if (matchAndConsume(IDENTIFIER)) Ident(prevToken.lexeme)
        else throw parseError("expected identifier$where")

    private fun tuple(): Expr.Tuple {
        consume(LEFT_PAREN, " at tuple start")
        return tuplePartial(expression())
    }

    private fun tuplePartial(firstItem: Expr): Expr.Tuple {
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
