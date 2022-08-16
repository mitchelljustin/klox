class Context(val enclosing: Context? = null) {
    class NotFoundError(target: String) : Exception("value not found: '$target'")

    private var binding = HashMap<String, Value>()

    fun resolve(target: String): Value =
        binding.getOrElse(target) { enclosing?.resolve(target) } ?: throw NotFoundError(target)

    fun defineVar(target: String, init: Value = null): Value {
        binding[target] = init
        return init
    }

    fun defineFun(target: String, function: Callable) = defineVar(target, function)

    fun assign(target: String, value: Value): Boolean {
        if (target !in binding)
            return enclosing?.assign(target, value) ?: false
        binding[target] = value
        return true
    }

}