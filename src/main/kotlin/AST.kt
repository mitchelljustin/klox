abstract class AST

class Program(val stmts: List<Stmt>) : AST() {
    override fun toString() = "Program(\n${stmts.joinToString("\n")}\n)"
}

open class Stmt : AST() {
    data class ExprStmt(val expr: Expr) : Stmt()

    data class PrintStmt(val expr: Expr) : Stmt()


    data class VariableDecl(val target: Ident, val init: Expr?) : Stmt()
}

open class Expr : AST() {
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()

    data class Grouping(val expression: Expr) : Expr()

    data class Unary(val operator: Token, val right: Expr) : Expr()

    data class Variable(val variable: Ident) : Expr()

    data class Assignment(val target: Ident, val value: Expr) : Expr()

    class Literal(val value: Any?) : Expr() {
        override fun toString() = when (value) {
            is String -> "\"$value\""
            is Double -> value.toString()
            true -> "true"
            false -> "false"
            null -> "null"
            else -> "?"
        }
    }
}

data class Ident(val name: String) : AST()
