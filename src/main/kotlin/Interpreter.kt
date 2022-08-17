import TokenType.*


typealias MatchResult = ArrayList<Pair<String, Value>>

class Interpreter {
    companion object {
        const val LastValue = "_"

    }

    open class RuntimeError(message: String, ast: AST? = null) : Exception(
        when (ast) {
            null -> message
            else -> "$message: $ast"
        }
    )

    class TypeError(where: String, expected: String, actual: String? = null) : RuntimeError(
        "expected type $expected$where${if (actual != null) ", got $actual" else ""}"
    )

    class Return(val value: Value) : Exception()
    class Break : Exception()

    private val global = Context()
    private var ctx = global
    private var lastValue: Value
        get() = global.resolve(LastValue)
        set(value) {
            global.assign(LastValue, value)
        }

    init {
        global.define(Callable.BuiltIn("print") { value -> println(value); Value.Nil })
        global.define(Callable.BuiltIn("readLine") { -> Value(readLine()) })
        global.define(Callable.BuiltIn("clock") { -> Value(System.currentTimeMillis() / 1000.0) })

        global.define(Callable.BuiltIn("string") { value -> Value(value.toString()) })
        global.define(Callable.BuiltIn("number") { value -> value.map<Any?> { it as? Double } })
        global.define(Callable.BuiltIn("type") { value -> value.typeAtom })
        global.define(LastValue)
    }

    fun interpret(program: Program) = try {
        val result = execSequence(program.stmts)
        clearLastValue()
        result
    } catch (_: Break) {
        throw RuntimeError("illegal break outside of loop")
    }

    private fun execSequence(stmts: Iterable<Stmt>): Value =
        try {
            stmts.forEach(::exec)
            lastValue
        } catch (ret: Return) {
            ret.value
        }

    private fun contextPush(function: Callable.FunctionDef? = null): Context {
        ctx = Context(ctx, function)
        return ctx
    }

    private fun contextPop() {
        if (ctx === global)
            throw RuntimeError("cannot pop global context")
        ctx = ctx.enclosing!!
    }

    private fun exec(stmt: Stmt) {
        when (stmt) {
            is Stmt.VariableDecl -> execVariableDecl(stmt)
            is Stmt.FunctionDef -> execFunctionDef(stmt)
            is Stmt.ForIn -> execForIn(stmt)
            is Stmt.While -> execWhile(stmt)
            is Stmt.Break -> throw Break()
            is Stmt.Return -> execReturn(stmt)
            is Stmt.ExprStmt -> execExprStmt(stmt)
            else ->
                throw RuntimeError("illegal stmt type", stmt)
        }
    }

    private fun evalMatch(expr: Expr.Match): Value {
        val value = eval(expr.target)
        for (clause in expr.clauses) {
            val bindings = matchesPattern(value, clause.pattern)
            if (bindings != null) {
                contextPush()
                bindings.forEach { (name, value) -> ctx.define(name, value) }
                val result = execExprStmt(clause.body)
                contextPop()
                return result
            }
        }
        // TODO: exhausted match?
        return Value.Nil
    }


    private fun matchesPattern(value: Value, pattern: MatchPattern): MatchResult? = when (pattern) {
        is MatchPattern.Anything -> {
            if (pattern.capture != null)
                arrayListOf(pattern.capture.name to value)
            else
                arrayListOf()
        }
        is MatchPattern.Literal ->
            if (value == eval(pattern.value)) arrayListOf() else null
        is MatchPattern.List ->
            matchesListPattern(value, pattern)
        is MatchPattern.Dict ->
            matchesDictPattern(value, pattern)
        else -> throw RuntimeError("unimplemented pattern type: ${pattern::class.simpleName}", pattern)
    }

    private fun matchesDictPattern(value: Value, pattern: MatchPattern.Dict): MatchResult? {
        if (!value.isDict) return null
        val dict = value.intoDict()
        val captures = MatchResult()
        for ((key, valuePattern) in pattern.entries) {
            val item = dict[key] ?: return null
            val capture = matchesPattern(item, valuePattern) ?: return null
            captures.addAll(capture)
        }
        return captures
    }

    private fun matchesListPattern(value: Value, pattern: MatchPattern.List): MatchResult? {
        if (!value.isList) return null
        val list = value.intoList()
        if (list.size != pattern.items.size) return null
        return try {
            list
                .zip(pattern.items)
                .fold(MatchResult()) { captures, (itemValue, itemPattern) ->
                    when (val capture = matchesPattern(itemValue, itemPattern)) {
                        null -> throw Break()
                        else -> (captures + capture) as MatchResult
                    }
                }
        } catch (_: Break) {
            null
        }
    }

    private fun execExprStmt(stmt: Stmt.ExprStmt): Value {
        val result = eval(stmt.expr)
        lastValue = if (stmt.emitValue) result else Value.Nil
        return lastValue
    }

    private fun execFunctionDef(stmt: Stmt.FunctionDef) = ctx.define(stmt.name.name, Value(Callable.FunctionDef(stmt)))

    private fun execVariableDecl(stmt: Stmt.VariableDecl) {
        val value = if (stmt.init != null) eval(stmt.init) else Value.Nil
        val name = stmt.name.name
        ctx.define(name, value)
    }

    private fun evalIf(stmt: Expr.If): Value {
        val condition = eval(stmt.condition)
        return when {
            condition.isTruthy ->
                evalBlock(stmt.ifBody)
            stmt.elseBody != null ->
                evalBlock(stmt.elseBody)
            else -> Value.Nil
        }
    }

    private fun execReturn(stmt: Stmt.Return): Value {
        if (ctx.enclosingFunction == null)
            throw RuntimeError("returning outside of a function")
        val result = if (stmt.retVal == null) Value.Nil else eval(stmt.retVal)
        contextPop()
        throw Return(result)
        @Suppress("UNREACHABLE_CODE")
        return Value.Nil
    }

    private fun clearLastValue() {
        lastValue = Value.Nil
    }

    private fun execWhile(stmt: Stmt.While): Value {
        while (eval(stmt.condition).isTruthy) {
            try {
                evalBlock(stmt.body)
            } catch (_: Break) {
                break
            }
        }
        return Value.Nil
    }

    private fun execForIn(stmt: Stmt.ForIn): Value {
        val ctx = contextPush()
        val iterators: List<String> = when (stmt.iterator) {
            is Expr.Variable -> listOf(stmt.iterator.target.name)
            is Expr.Tuple -> stmt.iterator.elements.map { (it as Expr.Variable).target.name }
            else -> throw RuntimeError("unsupported iterator type, check your parser")
        }
        iterators.forEach { ctx.define(it) }
        val iteratee = eval(stmt.iteratee)
        try {
            doIteration(stmt, ctx, iteratee, iterators)
        } catch (_: Break) {
        }
        clearLastValue()
        contextPop()
        return Value.Nil
    }

    private fun doIteration(
        stmt: Stmt.ForIn,
        ctx: Context,
        iteratee: Value,
        iterators: List<String>,
    ) {
        when {
            iteratee.isRange -> {
                val (start, end) = iteratee.intoPair()
                if (!start.isInt || !end.isInt)
                    throw RuntimeError("range components must be integers")
                if (iterators.size != 1)
                    throw RuntimeError("range iterator can only have a single variable")
                val range = start.intoInt() until end.intoInt()
                for (i in range) {
                    ctx.assign(iterators.first(), Value(i))
                    evalBlock(stmt.body)
                }
            }
            iteratee.isList -> {
                for (item in iteratee.intoList()) {
                    when {
                        item.isList && iterators.size == item.intoList().size ->
                            iterators
                                .zip(item.intoList())
                                .forEach { (name, element) -> ctx.assign(name, element) }
                        iterators.size == 1 ->
                            ctx.assign(iterators.first(), item)
                        else ->
                            throw RuntimeError("wrong number of variables")
                    }
                    evalBlock(stmt.body)
                }
            }
            iteratee.isDict -> {
                for ((k, v) in iteratee.intoDict()) {
                    if (iterators.size != 2)
                        throw RuntimeError("need (k, v) tuple as iterator for dict")
                    ctx.assign(iterators[0], Value(k))
                    ctx.assign(iterators[1], v)
                    evalBlock(stmt.body)
                }
            }
            else -> throw RuntimeError("illegal loop iterator: ${iteratee.type}")
        }
    }

    private fun evalBlock(stmt: Expr.Block): Value {
        contextPush()
        val result = execSequence(stmt.stmts)
        contextPop()
        return result
    }

    private fun eval(expr: Expr): Value = when (expr) {
        is Expr.Literal -> evalLiteral(expr)
        is Expr.Range -> evalRange(expr)
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> eval(expr.expression)
        is Expr.Variable -> evalVariable(expr)
        is Expr.Call -> evalCall(expr)
        is Expr.Assignment -> evalAssignment(expr)
        is Expr.Access -> evalAccess(expr)
        is Expr.Block -> evalBlock(expr)
        is Expr.If -> evalIf(expr)
        is Expr.Match -> evalMatch(expr)
        is Expr.Index -> evalIndex(expr)
        else -> throw RuntimeError("illegal expr type", expr)
    }

    private fun evalIndex(expr: Expr.Index): Value {
        val target = eval(expr.target)
        val indexValue = eval(expr.index)
        return when {
            target.isList -> {
                val index = indexInt(indexValue, " for list index")
                target.intoList()[index]
            }
            target.isDict -> {
                val key = indexDictKey(indexValue, " for dict key")
                target.intoDict()[key] ?: Value.Nil
            }
            target.isString -> {
                val index = indexInt(indexValue, " for string index")
                val string = target.into<String>()
                if (index >= string.length)
                    return Value.Nil // TODO: error handling ??
                val char = string[index].toString()
                Value(char)
            }
            else -> throw TypeError(" in index expression target", "List or Dict", target.type.toString())
        }
    }

    private fun indexDictKey(indexValue: Value, where: String): String = when {
        indexValue.isString -> indexValue.into()
        indexValue.isAtom -> indexValue.into<Atom>().name
        else -> throw TypeError(where, "String or Atom", indexValue.type.toString())
    }

    private fun indexInt(index: Value, where: String): Int {
        if (!index.isInt) throw TypeError(where, "Int", index.type.toString())
        return index.intoInt()
    }

    private fun evalRange(expr: Expr.Range) = Value.Range(eval(expr.start), eval(expr.end))

    private fun evalVariable(expr: Expr.Variable) = ctx.resolve(expr.target.name)

    private fun evalLiteral(expr: Expr.Literal) = Value(when (val value = expr.value) {
        is ArrayList<*> -> value.map { eval(it as Expr) }
        is HashMap<*, *> -> value.mapValues { (_, v) -> eval(v as Expr) }
        else -> value
    })

    private fun evalAccess(expr: Expr.Access): Value {
        val target = eval(expr.target)
        val member = expr.member.name
        return when {
            target.isDict && member == "length" -> Value(target.intoDict().size)
            target.isList && member == "length" -> Value(target.intoList().size)
            else -> throw RuntimeError("illegal access: '$member' on ${target.type}")
        }
    }

    private fun evalAssignment(expr: Expr.Assignment): Value = when (expr.target) {
        is Expr.Variable -> {
            val target = expr.target.target.name
            var newValue = eval(expr.value)
            val operator = expr.operator
            val undefinedVar = { throw RuntimeError("undefined variable '$target'", expr) }
            if (operator.type != EQUAL) {
                val oldValue = ctx.resolveSafe(target) ?: undefinedVar()
                newValue = Value(applyBinaryOp(operator, oldValue.into(), newValue.into()))
            }
            if (!ctx.assign(target, newValue)) undefinedVar()

            newValue
        }
        is Expr.Access -> TODO("not implemented")
        is Expr.Index -> {
            val target = eval(expr.target.target)
            val indexValue = eval(expr.target.index)
            when {
                target.isList -> {
                    val index = indexInt(indexValue, " for list assignment index")
                    val value = eval(expr.value)
                    target.intoList()[index] = value
                    value
                }
                target.isDict -> {
                    val key = indexDictKey(indexValue, " for dict assignment key")
                    val value = eval(expr.value)
                    target.intoDict()[key] = value
                    value
                }
                else -> throw TypeError(" for index assignment", "Dict or List", target.type.toString())
            }
        }
        else -> throw RuntimeError("illegal assignment target: ${expr.target::class.simpleName}")
    }

    private fun evalCall(expr: Expr.Call): Value {
        val callee: Callable
        try {
            callee = eval(expr.target).into()
        } catch (err: Value.CastException) {
            throw TypeError(" for function call", "Callable", err.value.type.toString())
        }
        val callArity = expr.arguments.size
        if (callee.arity != callArity)
            throw RuntimeError("callee expected arity ${callee.arity}, got $callArity", expr)
        val arguments = expr.arguments.map(::eval)
        val result = doCall(callee, arguments)
        return Value(result)
    }

    private fun doCall(callee: Callable, arguments: List<Value>): Value = when (callee) {
        is Callable.BuiltIn -> callee.call(arguments)
        is Callable.FunctionDef -> {
            val ctx = contextPush(function = callee)
            for ((param, arg) in callee.def.parameters.zip(arguments))
                ctx.define(param.name, arg)
            val result = evalBlock(callee.def.body)
            if (ctx === this.ctx) // implicit return at end of function call
                contextPop()
            result
        }
        is Callable.Method ->
            doCall(callee.function, listOf(callee.self) + arguments)
        else ->
            throw RuntimeError("illegal Callable $callee : ${callee::class.simpleName}")
    }

    private fun evalUnaryExpr(expr: Expr.Unary): Value {
        val right = eval(expr.right)
        return when (expr.operator.type) {
            MINUS ->
                if (right.isDouble) right.map<Double> { -it }
                else throw RuntimeError("rhs must be double for unary minus", expr)
            BANG ->
                Value(!right.isTruthy)
            else -> throw RuntimeError("unexpected unary operator", expr)
        }
    }

    private fun evalBinaryExpr(expr: Expr.Binary): Value {
        val operator = expr.operator
        val leftObj = eval(expr.left)
        val logicalResult = when (operator.type) {
            AND -> if (leftObj.isFalsy) leftObj else eval(expr.right)
            OR -> if (leftObj.isTruthy) leftObj else eval(expr.right)
            else -> null
        }
        if (logicalResult != null) return logicalResult
        val rightObj = eval(expr.right)
        // handle special non-Double cases
        when (operator.type) {
            PLUS ->
                if (leftObj.isString && rightObj.isString)
                    return Value(leftObj.into<String>() + rightObj.into<String>())
            EQUAL_EQUAL ->
                return Value(leftObj == rightObj)
            BANG_EQUAL ->
                return Value(leftObj != rightObj)
            else -> {}
        }
        return evalBinaryExprDouble(expr, leftObj, rightObj)
    }

    private fun evalBinaryExprDouble(expr: Expr.Binary, leftObj: Value, rightObj: Value): Value {
        val operator = expr.operator
        val left: Double
        val right: Double
        try {
            left = leftObj.into()
            right = rightObj.into()
        } catch (err: Value.CastException) {
            throw RuntimeError("both lhs and rhs must be doubles for $operator", expr)
        }
        return Value(
            applyBinaryOp(operator, left, right)
                ?: throw RuntimeError("unexpected operator for numbers", expr)
        )
    }

    private fun applyBinaryOp(operator: Token, left: Double, right: Double) = when (operator.type) {
        PLUS, PLUS_EQUAL -> left + right
        MINUS, MINUS_EQUAL -> left - right
        STAR, STAR_EQUAL -> left * right
        SLASH, SLASH_EQUAL -> left / right
        GREATER -> left > right
        GREATER_EQUAL -> left >= right
        LESS -> left < right
        LESS_EQUAL -> left <= right
        else -> null
    }
}