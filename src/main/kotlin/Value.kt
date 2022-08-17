import Atom as BareAtom

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
        Range,
        Dict,
        Nil,
    }

    companion object {
        val Nil = Value(null)
        val False = Value(false)
        val True = Value(true)

        @Suppress("FunctionName")
        fun Atom(name: String) = Value(BareAtom(name), Type.Atom)

        @Suppress("FunctionName")
        fun Range(start: Value, end: Value) = Value(Pair(start, end), Type.Range)
    }

    constructor(inner: Any?) : this(
        when (inner) {
            is Value -> inner.inner
            is ArrayList<*> -> inner.map(::Value)
            is HashMap<*, *> -> inner.entries.associate { (k, v) -> (k as String) to Value(v) }
            is Int -> inner.toDouble()
            else -> inner
        },
        when (inner) {
            is String -> Type.String
            is Double, is Int -> Type.Double
            is Boolean -> Type.Boolean
            is BareAtom -> Type.Atom
            is Function<*>, is Callable -> Type.Callable
            is ArrayList<*> -> Type.List
            is HashMap<*, *> -> Type.Dict
            null, is Unit -> Type.Nil
            is Value -> inner.type
            else -> throw Exception("cannot convert to Lox value: $inner")
        }
    )

    val isNil get() = type == Type.Nil
    val isString get() = type == Type.String
    val isList get() = type == Type.List
    val isDict get() = type == Type.Dict
    val isRange get() = type == Type.Range
    val isDouble get() = type == Type.Double
    val isInt get() = isDouble && inner is Double && inner - inner.toInt() == 0.0
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
    val typeAtom get() = Atom(type.toString())

    inline fun <reified T> map(f: (T) -> T): Value = Value(f(into()))
    inline fun <reified T> into(): T = when (inner) {
        is T -> inner
        else -> throw CastException(this, T::class.simpleName ?: "unknown")
    }

    fun intoList() = into<ArrayList<Value>>()
    fun intoDict() = into<HashMap<String, Value>>()
    fun intoPair() = into<Pair<Value, Value>>()
    fun intoInt() = into<Double>().toInt()

    override operator fun equals(other: Any?) = when (other) {
        is Value -> when {
            isNil && other.isNil -> true
            isNil -> false
            else -> type == other.type && inner == other.inner
        }
        else -> false
    }

    override fun toString() = when {
        isInt -> intoInt().toString()
        isDict && inner is HashMap<*, *> -> {
            var kvs = inner
                .map { (k, v) -> "$k: $v" }
                .joinToString(", ")
            if (kvs.isEmpty()) kvs = ":"
            "[$kvs]"
        }
        isRange && inner is Pair<*, *> -> "${inner.first}..${inner.second}"
        else -> inner.toString()
    }

    override fun hashCode(): Int {
        var result = inner?.hashCode() ?: 0
        result = 31 * result + type.hashCode()
        return result
    }

}
