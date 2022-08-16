interface Callable {
    val arity: Int

    data class BuiltIn(
        override val arity: Int,
        val call: (List<Value>) -> Value,
    ) : Callable {
        constructor(f: () -> Value) :
                this(0, { f() })

        constructor(f: (Value) -> Value) :
                this(1, { (a) -> f(a) })

        constructor(f: (Value, Value) -> Value) :
                this(2, { (a, b) -> f(a, b) })
    }

    data class FunctionDef(
        val parameters: List<String>,
        val body: Stmt.Block
    ) : Callable {
        override val arity: Int
            get() = parameters.size
    }
}


