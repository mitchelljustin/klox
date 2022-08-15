typealias Value = Any?

fun toValue(x: Any?): Value = when (x) {
    is Unit -> null
    else -> x
}