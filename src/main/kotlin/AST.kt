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

abstract class Stmt : AST() {
    data class ExprStmt(val expr: Expr, val emitValue: Boolean) : Stmt()

    data class Return(val retVal: Expr?) : Stmt()

    class Break : Stmt() {
        override fun toString() = "Break()"
    }

    data class While(val condition: Expr, val body: Expr.Block) : Stmt()

    data class ForIn(val iterator: Expr, val iteratee: Expr, val body: Expr.Block) : Stmt()

    data class VariableDecl(val name: Ident, val init: Expr?) : Stmt()

    data class FunctionDecl(val def: FunctionDef) : Stmt()
}

abstract class Expr : AST() {
    class Block(val stmts: List<Stmt>) : Expr() {
        override fun toString() = "Block(${listToString(stmts)})"
    }

    data class Function(val def: FunctionDef) : Expr()

    data class If(val condition: Expr, val ifBody: Block, val elseBody: Block?) : Expr()

    data class Match(val target: Expr, val clauses: List<MatchClause>) : Expr()

    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()

    data class Grouping(val expression: Expr) : Expr()

    data class Unary(val operator: Token, val right: Expr) : Expr()

    data class Variable(val target: Ident) : Expr() {
        override fun toString() = "Variable(${target.name})"
    }

    data class Assignment(val target: Expr, val operator: Token, val value: Expr) : Expr()

    data class Call(val target: Expr, val arguments: List<Expr>) : Expr()

    data class Index(val target: Expr, val index: Expr) : Expr()

    data class Access(val target: Expr, val member: Ident) : Expr()

    data class Tuple(val elements: List<Expr>) : Expr()

    data class Range(val start: Expr, val end: Expr) : Expr()

    class Literal(val value: Any?) : Expr() {
        override fun toString() = buildString {
            append("Literal(")
            append(
                when (value) {
                    is String -> "\"$value\""
                    else -> value.toString()
                }
            )
            append(")")
        }
    }
}

data class Ident(val name: String) : AST() {
    override fun toString() = "Ident($name)"
}

data class MatchClause(val pattern: MatchPattern, val body: Stmt.ExprStmt) : AST()

data class FunctionDef(val name: Ident?, val parameters: List<Ident>, val body: Expr.Block) : AST()

abstract class MatchPattern : AST() {
    data class Literal(val value: Expr.Literal) : MatchPattern()
    data class Anything(val capture: Ident?) : MatchPattern()
    data class List(val items: ArrayList<MatchPattern>) : MatchPattern()
    data class Dict(val entries: ArrayList<Pair<String, MatchPattern>>) : MatchPattern()
}