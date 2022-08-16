class Atom(val name: String) {
    override fun toString() = ":$name"
    override operator fun equals(other: Any?) = when (other) {
        is Atom -> name == other.name
        else -> false
    }

    override fun hashCode() = name.hashCode()
}