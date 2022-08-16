class Atom(val name: String) {
    override fun toString() = when {
        name.firstOrNull() in 'A'..'Z' ->
            name
        else ->
            ":$name"
    }

    override operator fun equals(other: Any?) = when (other) {
        is Atom -> name == other.name
        else -> false
    }

    override fun hashCode() = name.hashCode()
}