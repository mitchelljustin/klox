import TokenType.*

class Interpreter {
    companion object {
        const val LastValue = "_"
    }

    class RuntimeError(message: String, ast: AST? = null) : Exception(
        when (ast) {
            null -> message
            else -> "$message: $ast"
        }
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

        global.define(Callable.BuiltIn("string") { value -> value.map<Any?> { it.toString() } })
        global.define(Callable.BuiltIn("number") { value -> value.map<Any?> { it as? Double } })
        global.define(LastValue)
    }

    fun interpret(program: Program) = execSequence(program.stmts)

    private fun execSequence(stmts: Iterable<Stmt>): Value = try {
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
            is Stmt.VariableDecl -> {
                val value = if (stmt.init != null) eval(stmt.init) else Value.Nil
                val name = stmt.name.name
                ctx.define(name, value)
                Value.Nil
            }
            is Stmt.FunctionDef -> {
                ctx.define(stmt.name.name, Value(Callable.FunctionDef(stmt)))
            }
            is Stmt.If -> {
                val condition = eval(stmt.condition)
                when {
                    condition.isTruthy ->
                        exec(stmt.ifBody)
                    stmt.elseBody != null ->
                        exec(stmt.elseBody)
                    else -> Value.Nil
                }
            }
            is Stmt.For -> {
                if (stmt.init != null) exec(stmt.init)
                val ctx = ctx
                while (stmt.condition == null || eval(stmt.condition).isTruthy) {
                    try {
                        exec(stmt.body)
                    } catch (_: Break) {
                        this.ctx = ctx
                        break
                    }
                    if (stmt.update != null) eval(stmt.update)
                }
                Value.Nil
            }
            is Stmt.Break -> {
                throw Break()
            }
            is Stmt.Return -> {
                if (ctx.function == null)
                    throw RuntimeError("returning outside of a function")
                val result = eval(stmt.expr)
                contextPop()
                throw Return(result)
            }
            is Stmt.Block -> {
                contextPush()
                execSequence(stmt.stmts)
                contextPop()
                Value.Nil
            }
            is Stmt.ExprStmt -> {
                val value = eval(stmt.expr)
                if (stmt.emitValue) value else Value.Nil
            }
            else ->
                throw RuntimeError("unknown stmt type", stmt)
        }
        return lastValue
    }

    private fun eval(expr: Expr): Value = when (expr) {
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Literal -> Value(expr.value)
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> eval(expr.expression)
        is Expr.Variable -> ctx.resolve(expr.target.name)
        is Expr.Call -> {
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
            Value(result)
        }
        is Expr.Assignment -> {
            val target = expr.target.name
            var newValue = eval(expr.value)
            val operator = expr.operator
            val undefinedVar = { throw RuntimeError("undefined variable '$target'", expr) }
            if (operator.type != EQUAL) {
                val oldValue = ctx.resolveSafe(target) ?: undefinedVar()
                newValue = Value(applyBinaryOp(operator, newValue.into(), oldValue.into()))
            }
            if (!ctx.assign(target, newValue)) undefinedVar()

            newValue
        }
        else -> throw RuntimeError("unknown expr type", expr)
    }

    private fun doCall(callee: Callable, arguments: List<Value>): Value = when (callee) {
        is Callable.BuiltIn -> callee.call(arguments)
        is Callable.FunctionDef -> {
            val ctx = contextPush(function = callee)
            for ((param, arg) in callee.def.parameters.zip(arguments))
                ctx.define(param.name, arg)
            val result = execSequence(callee.def.body.stmts)
            if (ctx === this.ctx)
                contextPop()
            result
        }
        else ->
            throw RuntimeError("unsupported Callable $callee")
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
                return Value(isEqual(leftObj, rightObj))
            BANG_EQUAL ->
                return Value(!isEqual(leftObj, rightObj))
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


    private fun isEqual(a: Value, b: Value) = when {
        a.isNil && b.isNil ->
            true
        a.isNil ->
            false
        else ->
            a == b
    }
}