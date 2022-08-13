class Environment {
    private var binding = HashMap<String, Value>()

    operator fun get(ident: String): Value = binding[ident]

    fun define(target: String, init: Value = null) {
        binding[target] = init
    }

    fun assign(target: String, value: Value): Boolean {
        if (target !in binding) return false
        binding[target] = value
        return true
    }

}