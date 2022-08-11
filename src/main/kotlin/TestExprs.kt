import TokenType.MINUS
import TokenType.PLUS

fun main() {
    val ast = Expr.Binary(
        Expr.Literal(1.5),
        Token(PLUS, "+", 0),
        Expr.Grouping(
            Expr.Unary(
                Token(MINUS, "-", 0),
                Expr.Literal("swag")
            )
        )
    )
    println(ast)
}