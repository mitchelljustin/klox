open class Value(val inner: Any?, val type: Type) {
    class CastException(value: Value, targetType: String) :
        TypeCastException(
            when (value.inner) {
                null -> "failed cast from null to $targetType"
                else -> "failed cast from ${value::class.simpleName} to $targetType"
            }
        )

    enum class Type {
        String,
        Double,
        Boolean,
        Callable,
        Atom,
        List,
        Nil,
    }

    companion object {
        val Nil = Value(null)
        val False = Value(false)
        val True = Value(true)

        @Suppress("FunctionName")
        fun Atom(name: String) = Value(name, Type.Atom)
    }

    constructor(inner: Any?) : this(
        when (inner) {
            is Value -> inner.inner
            is ArrayList<*> -> inner.map { Value(it) }
            is Atom -> inner.name // intern the atom
            else -> inner
        },
        when (inner) {
            is String -> Type.String
            is Double -> Type.Double
            is Boolean -> Type.Boolean
            is Atom -> Type.Atom
            is Function<*>, is Callable -> Type.Callable
            is ArrayList<*> -> Type.List
            null, is Unit -> Type.Nil
            is Value -> inner.type
            else -> throw Exception("cannot convert to Lox value: $inner")
        }
    )

    val isNil get() = type == Type.Nil
    val isString get() = type == Type.String
    val isList get() = type == Type.List
    val isDouble get() = type == Type.Double
    val isBoolean get() = type == Type.Boolean
    val isAtom get() = type == Type.Atom
    val isCallable get() = type == Type.Callable
    val isTruthy
        get() = when {
            isNil -> false
            isBoolean -> into()
            else -> true
        }
    val isFalsy get() = !isTruthy
    val typeAtom: Value
        get() = Atom(
            when (type) {
                Type.String -> "String"
                Type.Double -> "Double"
                Type.Boolean -> "Boolean"
                Type.Callable -> "Callable"
                Type.Atom -> "Atom"
                Type.List -> "List"
                Type.Nil -> "Nil"
            }
        )

    inline fun <reified T> map(f: (T) -> T): Value = Value(f(into()))
    inline fun <reified T> into(): T = when (inner) {
        is T -> inner
        else -> throw CastException(this, T::class.simpleName ?: "unknown")
    }

    override operator fun equals(other: Any?) = when (other) {
        is Value -> when {
            isNil && other.isNil -> true
            isNil -> false
            else -> type == other.type && inner == other.inner
        }
        else -> false
    }

    override fun toString() = when {
        inner is Double && inner - inner.toInt() == 0.0 ->
            inner.toInt().toString()
        isString -> "\"$inner\""
        else -> inner.toString()
    }

    override fun hashCode(): Int {
        var result = inner?.hashCode() ?: 0
        result = 31 * result + type.hashCode()
        return result
    }

}
