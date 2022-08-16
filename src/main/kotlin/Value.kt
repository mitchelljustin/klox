enum class VType {
    String,
    Double,
    Boolean,
    Callable,
    Null,
}


class Value(val inner: Any?, val type: VType) {
    constructor(inner: Any?) : this(
        when (inner) {
            is Value -> inner.inner
            else -> inner
        },
        when (inner) {
            is String -> VType.String
            is Double -> VType.Double
            is Boolean -> VType.Boolean
            is Function<*>, is Callable -> VType.Callable
            null -> VType.Null
            is Value -> inner.type
            else -> throw Exception("cannot convert to Lox value: $inner")
        }
    )

    val isNull get() = type == VType.Null
    val isString get() = type == VType.String
    val isDouble get() = type == VType.Double
    val isBoolean get() = type == VType.Boolean
    val isCallable get() = type == VType.Callable

    override operator fun equals(other: Any?) = when (other) {
        is Value -> type == other.type && inner == other.inner
        else -> false
    }

    override fun toString() = inner.toString()
    override fun hashCode(): Int {
        var result = inner?.hashCode() ?: 0
        result = 31 * result + type.hashCode()
        return result
    }

    companion object {
        val Null: Value = Value(null, VType.Null)
    }
}
