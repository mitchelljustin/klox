import TokenType.*

class Interpreter {

    class RuntimeError(message: String, ast: AST? = null) :
        kotlin.Exception("$message ${ast ?: ""}")

    private val global = Environment()
    private var environment = global

    fun interpret(program: Program) = execStmts(program.stmts)

    private fun execStmts(stmts: List<Stmt>): Value {
        var lastValue: Value = null
        for (stmt in stmts) {
            lastValue = exec(stmt)
        }
        return lastValue
    }

    private fun scopePush() {
        environment = Environment(environment)
    }

    private fun scopePop() {
        if (environment === global) throw RuntimeError("cannot pop global scope")
        environment = environment.parent!!
    }

    private fun exec(stmt: Stmt): Value = when (stmt) {
        is Stmt.VariableDecl -> {
            val value = if (stmt.init != null) eval(stmt.init) else null
            val target = stmt.target.name
            environment.define(target, value)
            value
        }
        is Stmt.Block -> {
            scopePush()
            val result = execStmts(stmt.stmts)
            scopePop()
            result
        }
        is Stmt.PrintStmt -> {
            println(eval(stmt.expr))
            null
        }
        is Stmt.ExprStmt -> eval(stmt.expr)
        else -> throw RuntimeError("unknown stmt type", stmt)
    }

    private fun eval(expr: Expr): Value = when (expr) {
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Literal -> expr.value
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> eval(expr.expression)
        is Expr.Variable -> environment[expr.variable.name]
        is Expr.Assignment -> {
            val target = expr.target.name
            val value = eval(expr.value)
            if (!environment.assign(target, value))
                throw RuntimeError("undefined variable '$target'", expr)

            value
        }
        else -> throw RuntimeError("unknown expr type", expr)
    }

    private fun evalUnaryExpr(expr: Expr.Unary): Value {
        val right = eval(expr.right)
        return when (expr.operator.type) {
            MINUS ->
                if (right is Double) -right
                else throw RuntimeError("rhs must be double for unary minus", expr)
            BANG -> !isTruthy(right)
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