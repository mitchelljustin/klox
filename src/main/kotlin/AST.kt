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

    data class Return(val retVal: Expr?) : Stmt()

    class Break : Stmt() {
        override fun toString() = "Break()"
    }

    data class If(val condition: Expr, val ifBody: Block, val elseBody: Block?) : Stmt()

    data class While(val condition: Expr, val body: Block) : Stmt()

    data class For(val init: Stmt?, val condition: Expr?, val update: Expr?, val body: Block) : Stmt()

    data class ForIn(val iterator: Ident, val iteratee: Expr, val body: Block) : Stmt()

    data class VariableDecl(val name: Ident, val init: Expr?) : Stmt()

    data class FunctionDef(val name: Ident, val parameters: List<Ident>, val body: Block) : Stmt()
}

open class Expr : AST() {
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()

    data class Grouping(val expression: Expr) : Expr()

    data class Unary(val operator: Token, val right: Expr) : Expr()

    data class Variable(val target: Ident) : Expr() {
        override fun toString() = "Variable(${target.name})"
    }

    data class Assignment(val target: Ident, val operator: Token, val value: Expr) : Expr()

    data class Call(val target: Expr, val arguments: List<Expr>) : Expr()

    data class Access(val target: Expr, val member: Ident) : Expr()

    class Literal(val value: Any?) : Expr() {
        override fun toString() = when (value) {
            is String -> "\"$value\""
            is Double, is Atom, is List<*>, true, false, null -> value.toString()
            else -> "?"
        }
    }
}

data class Ident(val name: String) : AST() {
    override fun toString() = "Ident($name)"
}
