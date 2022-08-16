class Value(val inner: Any?, val type: Type) {
    enum class Type {
        String,
        Double,
        Boolean,
        Callable,
        Nil,
    }

    companion object {
        val Nil = Value(null, Type.Nil)
    }

    constructor(inner: Any?) : this(
        when (inner) {
            is Value -> inner.inner
            else -> inner
        },
        when (inner) {
            is String -> Type.String
            is Double -> Type.Double
            is Boolean -> Type.Boolean
            is Function<*>, is Callable -> Type.Callable
            null, is Unit -> Type.Nil
            is Value -> inner.type
            else -> throw Exception("cannot convert to Lox value: $inner")
        }
    )

    val isNil get() = type == Type.Nil
    val isString get() = type == Type.String
    val isDouble get() = type == Type.Double
    val isBoolean get() = type == Type.Boolean
    val isCallable get() = type == Type.Callable

    fun <T> map(f: (T) -> T): Value = Value(f(into()))
    fun <T> into(): T = inner as T

    override operator fun equals(other: Any?) = when (other) {
        is Value -> type == other.type && inner == other.inner
        else -> false
    }

    override fun toString() = when {
        inner is Double && inner - inner.toInt() == 0.0 ->
            inner.toInt().toString()
        else ->
            inner.toString()
    }

    override fun hashCode(): Int {
        var result = inner?.hashCode() ?: 0
        result = 31 * result + type.hashCode()
        return result
    }
}
