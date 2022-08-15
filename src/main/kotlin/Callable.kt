interface Callable {
    companion object {
        fun lambda(f: () -> Value) = object : Callable {
            override val arity: Int
                get() = 0

            override fun call(vararg arguments: Value) = f()
        }

        fun lambda(f: (Value) -> Value) = object : Callable {
            override val arity: Int
                get() = 1

            override fun call(vararg arguments: Value) = f(arguments[0])
        }
    }

    val arity: Int

    fun call(vararg arguments: Value): Value
}


