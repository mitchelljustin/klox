class Context(
    val enclosing: Context? = null,
    private val function: Callable.FunctionDef? = null,
) {
    class NotFoundError(target: String) : Exception("value not found: '$target'")

    private var binding = HashMap<String, Value>()

    val enclosingFunction: Callable.FunctionDef?
        get() = function ?: enclosing?.enclosingFunction

    fun resolve(target: String): Value =
        resolveSafe(target) ?: throw NotFoundError(target)

    fun resolveSafe(target: String): Value? =
        binding.getOrElse(target) { enclosing?.resolve(target) }


    fun define(builtIn: Callable.BuiltIn) {
        define(builtIn.name, Value(builtIn))
    }

    fun define(name: String, init: Value = Value.Nil): Value {
        binding[name] = init
        return init
    }

    fun assign(target: String, value: Value): Boolean {
        if (target !in binding)
            return enclosing?.assign(target, value) ?: false
        binding[target] = value
        return true
    }

}