abstract class AST {
    companion object {
        fun listToString(list: List<AST>) = list.joinToString("\n") { "  $it," }
    }
}

class Program(val stmts: List<Stmt>) : AST() {
    override fun toString() = "Program(\n${listToString(stmts)}\n)"
}

open class Stmt : AST() {
    class Block(val stmts: List<Stmt>) : Stmt() {
        override fun toString() = "Block(\n${listToString(stmts)}\n)"
    }

    data class ExprStmt(val expr: Expr) : Stmt()

    data class VariableDecl(val target: Ident, val init: Expr?) : Stmt()
}

open class Expr : AST() {
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()

    data class Grouping(val expression: Expr) : Expr()

    data class Unary(val operator: Token, val right: Expr) : Expr()

    data class Variable(val variable: Ident) : Expr()

    data class Assignment(val target: Ident, val value: Expr) : Expr()

    data class Call(val target: Expr, val arguments: List<Expr>) : Expr()

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
