interface Callable {
    val arity: Int

    data class BuiltIn(
        override val arity: Int,
        val name: String,
        val call: (List<Value>) -> Value,
    ) : Callable {
        constructor(name: String, f: () -> Value) :
                this(arity = 0, name, { f() })

        constructor(name: String, f: (Value) -> Value) :
                this(arity = 1, name, { (a) -> f(a) })

        constructor(name: String, f: (Value, Value) -> Value) :
                this(arity = 2, name, { (a, b) -> f(a, b) })

        override fun toString() = "builtin:$name/$arity"
    }

    data class FunctionDef(val def: Stmt.FunctionDef) : Callable {
        override val arity: Int
            get() = def.parameters.size

        override fun toString() = "${def.name.name}/$arity"
    }
}


