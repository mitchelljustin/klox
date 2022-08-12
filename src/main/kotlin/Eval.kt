typealias Result = Any?

object Eval {

    class EvalError(message: String) : kotlin.Exception(message)

    fun evalExpr(expr: Expr): Result = when (expr) {
        is Expr.Binary -> evalBinaryExpr(expr)
        is Expr.Literal -> expr.value
        is Expr.Unary -> evalUnaryExpr(expr)
        is Expr.Grouping -> evalExpr(expr.expression)
        else -> throw EvalError("unknown expr type")
    }

    private fun evalUnaryExpr(expr: Expr.Unary): Result {
        TODO("Not yet implemented")
    }

    private fun evalBinaryExpr(expr: Expr.Binary): Result {
        TODO("Not yet implemented")
    }
}