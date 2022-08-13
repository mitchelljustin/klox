class Environment {
    private var binding = HashMap<String, Value>()

    operator fun get(ident: String): Value = binding[ident]

    operator fun set(ident: String, value: Value) {
        binding[ident] = value
    }
}