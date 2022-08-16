import TokenType.*

class Interpreter {

    class RuntimeError(message: String, ast: AST? = null) : Exception("$message ${ast ?: ""}")

    private data class StmtResult(val value: Value, val escape: Boolean = false)

    private val global = Context()
    private var ctx = global

    init {
        global.define("print", Callable.BuiltIn { value -> println(value) })
        global.define("readLine", Callable.BuiltIn(::readLine))
    }

    fun interpret(program: Program) = execSequence(program.stmts)

    private fun execSequence(stmts: Iterable<Stmt>): Value {
        var lastValue: Value = null
        for (stmt in stmts) {
            val (value, escape) = exec(stmt)
            lastValue = value
            if (escape) break
        }
        return lastValue
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

    private fun exec(stmt: Stmt): StmtResult = when (stmt) {
        is Stmt.VariableDecl -> {
            val value = if (stmt.init != null) eval(stmt.init) else null
            val name = stmt.name.name
            ctx.define(name, value)
            StmtResult(value)
        }
        is Stmt.FunctionDef -> {
            val func = ctx.define(stmt.name.name, Callable.FunctionDef(stmt))
            StmtResult(func)
        }
        is Stmt.Return -> {
            if (ctx.function == null)
                throw RuntimeError("returning outside of a function")
            val result = eval(stmt.expr)
            contextPop()
            StmtResult(result, escape = true)
        }
        is Stmt.Block -> {
            contextPush()
            val result = execSequence(stmt.stmts)
            contextPop()
            StmtResult(result)
        }
        is Stmt.ExprStmt ->
            StmtResult(eval(stmt.expr))
        else ->
            throw RuntimeError("unknown stmt type", stmt)
    }

    private fun eval(expr: Expr): Value = when (expr) {
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Literal -> expr.value
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> eval(expr.expression)
        is Expr.Variable -> ctx.resolve(expr.target.name)
        is Expr.Call -> {
            val callee = eval(expr.target)
            if (callee !is Callable)
                throw RuntimeError("callee must be callable", expr.target)
            val callArity = expr.arguments.count()
            if (callee.arity != callArity)
                throw RuntimeError("call has the wrong arity: $callArity != ${callee.arity}", expr)
            val arguments = expr.arguments.map(::eval)
            val result = doCall(callee, arguments)
            toValue(result)
        }
        is Expr.Assignment -> {
            val target = expr.target.name
            val value = eval(expr.value)
            if (!ctx.assign(target, value))
                throw RuntimeError("undefined variable '$target'", expr)

            value
        }
        else -> throw RuntimeError("unknown expr type", expr)
    }

    private fun doCall(callee: Callable, arguments: List<Value>): Value = when (callee) {
        is Callable.BuiltIn ->
            callee.call(arguments)
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
                if (right is Double) -right
                else throw RuntimeError("rhs must be double for unary minus", expr)
            BANG ->
                !isTruthy(right)
            else -> throw RuntimeError("unexpected unary operator", expr)
        }
    }

    private fun evalBinaryExpr(expr: Expr.Binary): Value {
        val leftObj = eval(expr.left)
        val rightObj = eval(expr.right)
        val operator = expr.operator
        // handle special non-Double cases
        when (operator.type) {
            PLUS ->
                if (leftObj is String && rightObj is String)
                    return leftObj + rightObj
            EQUAL_EQUAL ->
                return isEqual(leftObj, rightObj)
            BANG_EQUAL ->
                return !isEqual(leftObj, rightObj)
            else -> {}
        }
        return evalBinaryExprDouble(expr, leftObj, rightObj)
    }

    private fun evalBinaryExprDouble(expr: Expr.Binary, leftObj: Value, rightObj: Value): Value {
        val operator = expr.operator
        val left: Double
        val right: Double
        try {
            left = leftObj as Double
            right = rightObj as Double
        } catch (err: ClassCastException) {
            throw RuntimeError("both lhs and rhs must be numbers for $operator", expr)
        }
        return when (operator.type) {
            PLUS -> left + right
            MINUS -> left - right
            STAR -> left * right
            SLASH -> left / right
            GREATER -> left > right
            GREATER_EQUAL -> left >= right
            LESS -> left < right
            LESS_EQUAL -> left <= right
            else -> throw RuntimeError("unexpected operator for numbers", expr)
        }
    }

    private fun isTruthy(value: Value) = when (value) {
        null -> false
        is Boolean -> value
        else -> true
    }

    private fun isEqual(a: Value, b: Value) = when {
        a == null && b == null ->
            true
        a == null ->
            false
        else ->
            a == b
    }
}