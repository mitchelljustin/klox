class Context(val enclosing: Context? = null, val function: Callable.FunctionDef? = null) {
    class NotFoundError(target: String) : Exception("value not found: '$target'")

    private var binding = HashMap<String, Value>()

    fun resolve(target: String): Value =
        binding.getOrElse(target) { enclosing?.resolve(target) } ?: throw NotFoundError(target)

    fun define(name: String, init: Value = null): Value {
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