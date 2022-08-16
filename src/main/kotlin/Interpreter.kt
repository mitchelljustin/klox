import TokenType.*

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

    class TypeError(expected: Value.Type, actual: Value.Type) : RuntimeError(
        "expected type $expected, got type $actual"
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
        execSequence(program.stmts)
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

    private fun exec(stmt: Stmt): Value {
        lastValue = when (stmt) {
            is Stmt.Block -> execBlock(stmt)
            is Stmt.VariableDecl -> execVariableDecl(stmt)
            is Stmt.FunctionDef -> execFunctionDef(stmt)
            is Stmt.If -> execIf(stmt)
            is Stmt.For -> execFor(stmt)
            is Stmt.ForIn -> execForIn(stmt)
            is Stmt.Match -> execMatch(stmt)
            is Stmt.While -> execWhile(stmt)
            is Stmt.Break -> throw Break()
            is Stmt.Return -> execReturn(stmt)
            is Stmt.ExprStmt -> execExprStmt(stmt)
            else ->
                throw RuntimeError("unknown stmt type", stmt)
        }
        return lastValue
    }

    private fun execMatch(stmt: Stmt.Match): Value {
        val value = eval(stmt.expr)
        for (clause in stmt.clauses) {
            val ctx = matchesPattern(value, clause.pattern)
            if (ctx != null) {
                val result = exec(clause.body)
                contextPop()
                return result
            }
        }
        // TODO: exhausted match?
        return Value.Nil
    }

    private fun matchesPattern(value: Value, pattern: MatchPattern): Context? = when (pattern) {
        is MatchPattern.Anything -> {
            val ctx = contextPush()
            if (pattern.capture != null)
                ctx.define(pattern.capture.name, value)
            ctx
        }
        is MatchPattern.Literal ->
            if (value == eval(pattern.value)) contextPush() else null
        else -> throw RuntimeError("unimplemented pattern type: ${pattern::class.simpleName}", pattern)
    }

    private fun execExprStmt(stmt: Stmt.ExprStmt): Value {
        val value = eval(stmt.expr)
        return if (stmt.emitValue) value else Value.Nil
    }

    private fun execFunctionDef(stmt: Stmt.FunctionDef) = ctx.define(stmt.name.name, Value(Callable.FunctionDef(stmt)))

    private fun execVariableDecl(stmt: Stmt.VariableDecl): Value {
        val value = if (stmt.init != null) eval(stmt.init) else Value.Nil
        val name = stmt.name.name
        ctx.define(name, value)
        return if (stmt.emitValue) value else Value.Nil
    }

    private fun execIf(stmt: Stmt.If): Value {
        val condition = eval(stmt.condition)
        return when {
            condition.isTruthy ->
                execBlock(stmt.ifBody)
            stmt.elseBody != null ->
                execBlock(stmt.elseBody)
            else -> Value.Nil
        }
    }

    private fun execFor(stmt: Stmt.For): Value {
        contextPush()
        if (stmt.init != null) exec(stmt.init)
        while (stmt.condition == null || eval(stmt.condition).isTruthy) {
            try {
                execBlock(stmt.body)
            } catch (_: Break) {
                break
            }
            if (stmt.update != null) eval(stmt.update)
        }
        contextPop()
        return Value.Nil
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

    private fun execWhile(stmt: Stmt.While): Value {
        while (eval(stmt.condition).isTruthy) {
            try {
                execBlock(stmt.body)
            } catch (_: Break) {
                break
            }
        }
        return Value.Nil
    }

    private fun execForIn(stmt: Stmt.ForIn): Value {
        val ctx = contextPush()
        val iterator = stmt.iterator.name
        ctx.define(iterator)
        val iteratee = eval(stmt.iteratee)
        when (iteratee.type) {
            Value.Type.List -> {
                for (item in iteratee.into<List<Value>>()) {
                    ctx.assign(iterator, item)
                    try {
                        execBlock(stmt.body)
                    } catch (_: Break) {
                        break
                    }
                }
            }
            else -> throw RuntimeError("unsupported loop iterator: ${iteratee.type}")
        }
        contextPop()
        return Value.Nil
    }

    private fun execBlock(stmt: Stmt.Block): Value {
        contextPush()
        val result = execSequence(stmt.stmts)
        contextPop()
        return result
    }

    private fun eval(expr: Expr): Value = when (expr) {
        is Expr.Literal -> evalLiteral(expr)
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> eval(expr.expression)
        is Expr.Variable -> evalVariable(expr)
        is Expr.Call -> evalCall(expr)
        is Expr.Assignment -> evalAssignment(expr)
        is Expr.Access -> evalAccess(expr)
        else -> throw RuntimeError("unknown expr type", expr)
    }

    private fun evalVariable(expr: Expr.Variable) = ctx.resolve(expr.target.name)

    private fun evalLiteral(expr: Expr.Literal) = Value(when (val value = expr.value) {
        is List<*> -> value.map { eval(it as Expr) }
        else -> value
    })

    private fun evalAccess(expr: Expr.Access): Value {
        val target = eval(expr.target)
        val member = expr.member.name
        return Value(
            when (member) {
                "string" -> Callable.Method(target, global.resolve("string").into())
                "type" -> Callable.Method(target, global.resolve("type").into())
                "add" -> {
                    if (!target.isList) throw TypeError(Value.Type.List, target.type)
                    Callable.Method(
                        target,
                        Callable.BuiltIn("add") { listValue, elem ->
                            listValue.map<ArrayList<Value>> {
                                it.add(elem); it
                            }
                        }
                    )
                }
                else -> throw RuntimeError("no member '$member' on $target")
            }
        )
    }

    private fun evalAssignment(expr: Expr.Assignment): Value {
        val target = expr.target.name
        var newValue = eval(expr.value)
        val operator = expr.operator
        val undefinedVar = { throw RuntimeError("undefined variable '$target'", expr) }
        if (operator.type != EQUAL) {
            val oldValue = ctx.resolveSafe(target) ?: undefinedVar()
            newValue = Value(applyBinaryOp(operator, oldValue.into(), newValue.into()))
        }
        if (!ctx.assign(target, newValue)) undefinedVar()

        return newValue
    }

    private fun evalCall(expr: Expr.Call): Value {
        val callee: Callable
        try {
            callee = eval(expr.target).into()
        } catch (err: Value.CastException) {
            throw RuntimeError("callee must be a Callable", expr.target)
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
            val result = execBlock(callee.def.body)
            if (ctx === this.ctx) // implicit return at end of function call
                contextPop()
            result
        }
        is Callable.Method ->
            doCall(callee.function, listOf(callee.self) + arguments)
        else ->
            throw RuntimeError("unsupported Callable $callee : ${callee::class.simpleName}")
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