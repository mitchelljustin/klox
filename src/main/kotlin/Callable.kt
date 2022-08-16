abstract class Callable {
    abstract val arity: Int
    abstract val name: String

    override fun toString() = "${name}/$arity"

    class BuiltIn(
        override val arity: Int,
        override val name: String,
        val call: (List<Value>) -> Value,
    ) : Callable() {
        constructor(name: String, f: () -> Value) :
                this(arity = 0, name, { f() })

        constructor(name: String, f: (Value) -> Value) :
                this(arity = 1, name, { (a) -> f(a) })

        constructor(name: String, f: (Value, Value) -> Value) :
                this(arity = 2, name, { (a, b) -> f(a, b) })

        override fun toString() = "builtin:$name/$arity"
    }

    class FunctionDef(val def: Stmt.FunctionDef) : Callable() {
        override val arity: Int
            get() = def.parameters.size
        override val name: String
            get() = def.name.name
    }

    class Method(val self: Value, val function: Callable) : Callable() {
        override val arity: Int
            get() = function.arity - 1
        override val name: String
            get() = "${self.type}.${function.name}"
    }
}


