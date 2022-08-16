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
                val result = runInterpreter(line)
                if (!result.isNil) println("=> $result")
            } catch (err: Exception) {
                println("!! ${err::class.simpleName} ${err.message}")
            }
        }
    }

    private fun runInterpreter(code: String): Value {
        if (code.trim().isEmpty()) return Value.Nil
        val tokens = Scanner(code).scan()
        println("## " + tokens.joinToString(" "))
        val expr = Parser(tokens).parse()
        println(expr.toString().lines().joinToString("\n") { "|| $it" })
        return interpreter.interpret(expr)
    }
}

fun main(args: Array<String>) = Lox().main(args)
