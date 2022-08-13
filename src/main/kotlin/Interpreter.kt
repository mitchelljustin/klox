import TokenType.*

class Interpreter {

    class RuntimeError(message: String, token: Token? = null, expr: Expr? = null) :
        kotlin.Exception("$message ${token ?: ""} ${expr ?: ""}")

    private var environment = Environment()

    fun interpret(program: Program): Value {
        var lastValue: Value = null
        for (stmt in program.stmts) {
            lastValue = exec(stmt)
        }
        return lastValue
    }

    private fun exec(stmt: Stmt): Value = when (stmt) {
        is Stmt.PrintStmt -> {
            println(eval(stmt.expr))
            null
        }
        is Stmt.ExprStmt -> eval(stmt.expr)
        else -> throw RuntimeError("unknown stmt type")
    }

    private fun eval(expr: Expr): Value = when (expr) {
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Literal -> expr.value
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> eval(expr.expression)
        else -> throw RuntimeError("unknown expr type")
    }

    private fun evalUnaryExpr(expr: Expr.Unary): Value {
        val right = eval(expr.right)
        return when (expr.operator.type) {
            MINUS ->
                if (right is Double) -right
                else throw RuntimeError("rhs must be double for unary minus")
            BANG -> !isTruthy(right)
            else -> throw RuntimeError("unexpected unary operator", expr.operator)
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
        return evalBinaryExprDouble(operator, leftObj, rightObj)
    }

    private fun evalBinaryExprDouble(operator: Token, leftObj: Value, rightObj: Value): Value {
        val left: Double
        val right: Double
        try {
            left = leftObj as Double
            right = rightObj as Double
        } catch (err: ClassCastException) {
            throw RuntimeError("both lhs and rhs must be double for operator", operator)
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
            else -> throw RuntimeError("unexpected operator for double", operator)
        }
    }

    private fun isTruthy(value: Value) = when (value) {
        null -> false
        is Boolean -> value
        else -> true
    }

    private fun isEqual(a: Value, b: Value): Boolean {
        if (a == null && b == null) return true
        if (a == null) return false
        return a == b
    }
}