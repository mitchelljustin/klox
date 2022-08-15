class Environment(val parent: Environment? = null) {
    class NotFoundError(target: String) : Exception("value not found: '$target'")

    private var binding = HashMap<String, Value>()

    fun resolve(target: String): Value =
        binding.getOrElse(target) { parent?.resolve(target) } ?: throw NotFoundError(target)

    fun define(target: String, init: Value = null) {
        binding[target] = init
    }

    fun defineFun(target: String, function: Callable) = define(target, function)

    fun assign(target: String, value: Value): Boolean {
        if (target !in binding)
            return parent?.assign(target, value) ?: false
        binding[target] = value
        return true
    }

}