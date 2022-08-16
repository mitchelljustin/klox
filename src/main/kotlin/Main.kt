import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess


class Lox {
    private var interpreter = Interpreter()

    fun main(args: Array<String>) {
        when (args.size) {
            0 -> runPrompt()
            1 -> runFile(args[0])

            else -> {
                println("Usage: klox [file]")
                exitProcess(64)
            }
        }
    }

    private fun runFile(filename: String) {
        val code = Files.readString(Paths.get(filename), Charset.defaultCharset())
        runInterpreter(code)
    }

    private fun runPrompt() {
        while (true) {
            print(">> ")
            val line = readLine() ?: break
            try {
                val result = runInterpreter(line, verbose = true)
                if (result != null) println("=> $result")
            } catch (err: Exception) {
                System.err.println("${err::class.simpleName} $err\n")
            }
        }
    }

    private fun runInterpreter(code: String, verbose: Boolean = false): Value {
        val tokens = Scanner(code).scan()
        val expr = Parser(tokens).parse()
        if (verbose) println(expr.toString().lines().joinToString("\n") { "|| $it" })
        return interpreter.interpret(expr)
    }
}

fun main(args: Array<String>) = Lox().main(args)
