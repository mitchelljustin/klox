open class AST

open class Expr : AST() {
    class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr() {
        override fun toString(): String = "$left ${operator.lexeme} $right"
    }

    class Grouping(val expression: Expr) : Expr() {
        override fun toString(): String = "( $expression )"
    }

    class Unary(val operator: Token, val right: Expr) : Expr() {
        override fun toString(): String = "${operator.lexeme} $right"
    }

    class Literal(val value: Any?) : Expr() {
        override fun toString(): String = when (value) {
            is String -> "\"$value\""
            is Double -> value.toString()
            true -> "true"
            false -> "false"
            null -> "null"
            else -> "?"
        }
    }
}

