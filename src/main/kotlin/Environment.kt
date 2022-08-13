class Environment(val parent: Environment? = null) {
    private var binding = HashMap<String, Value>()

    operator fun get(target: String): Value =
        binding.getOrElse(target) { parent?.get(target) }

    fun define(target: String, init: Value = null) {
        binding[target] = init
    }

    fun assign(target: String, value: Value): Boolean {
        if (target !in binding)
            return parent?.assign(target, value) ?: false
        binding[target] = value
        return true
    }

}