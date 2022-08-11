abstract class AST {
    open fun verbose() = toString()
}

open class Expr : AST() {
    class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr() {
        override fun toString() = "$left ${operator.lexeme} $right"

        override fun verbose() = "Binary(${left.verbose()}, $operator, ${right.verbose()})"
    }

    class Grouping(val expression: Expr) : Expr() {
        override fun toString() = "( $expression )"

        override fun verbose() = "Grouping(${expression.verbose()}"
    }

    class Unary(val operator: Token, val right: Expr) : Expr() {
        override fun toString() = "${operator.lexeme} $right"

        override fun verbose() = "Unary($operator, ${right.verbose()})"
    }

    class Literal(val value: Any?) : Expr() {
        override fun toString() = when (value) {
            is String -> "\"$value\""
            is Double -> value.toString()
            true -> "true"
            false -> "false"
            null -> "null"
            else -> "?"
        }

        override fun verbose() = "Literal(${toString()})"
    }
}

