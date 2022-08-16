abstract class AST {
    companion object {
        fun listToString(list: List<AST>) = when {
            list.isEmpty() -> ""
            else -> "\n" + list.joinToString("\n") { "  $it," }
        }
    }
}

class Program(val stmts: List<Stmt>) : AST() {
    override fun toString() = "Program(${listToString(stmts)})"
}

open class Stmt : AST() {
    class Block(val stmts: List<Stmt>) : Stmt() {
        override fun toString() = "Block(${listToString(stmts)})"
    }

    data class ExprStmt(val expr: Expr, val emitValue: Boolean) : Stmt()

    data class Return(val expr: Expr) : Stmt()

    class Break : Stmt() {
        override fun toString() = "Break()"
    }

    data class If(val condition: Expr, val ifBody: Stmt, val elseBody: Stmt?) : Stmt()

    data class For(val init: Stmt?, val condition: Expr?, val update: Expr?, val body: Stmt) : Stmt()

    data class VariableDecl(val name: Ident, val init: Expr?) : Stmt()

    data class FunctionDef(val name: Ident, val parameters: List<Ident>, val body: Block) : Stmt()
}

open class Expr : AST() {
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()

    data class Grouping(val expression: Expr) : Expr()

    data class Unary(val operator: Token, val right: Expr) : Expr()

    data class Variable(val target: Ident) : Expr()

    data class Assignment(val target: Ident, val value: Expr) : Expr()

    data class Call(val target: Expr, val arguments: List<Expr>) : Expr()

    class Literal(val value: Any?) : Expr() {
        override fun toString() = when (value) {
            is String -> "\"$value\""
            is Double -> value.toString()
            is Atom -> value.toString()
            true -> "true"
            false -> "false"
            null -> "null"
            else -> "?"
        }
    }
}

data class Ident(val name: String) : AST() {
    override fun toString() = "[$name]"
}
