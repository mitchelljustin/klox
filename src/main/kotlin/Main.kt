import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess


object Lox {
    private var hadError = false

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

    fun runFile(filename: String) {
        val code = Files.readString(Paths.get(filename), Charset.defaultCharset())
        runInterpreter(code)
        if (hadError) {
            exitProcess(65)
        }
    }

    fun runPrompt() {
        while (true) {
            print("> ")
            val line = readLine() ?: break
            runInterpreter(line)
            hadError = false
        }
    }

    fun runInterpreter(code: String) {
        val tokens = Scanner(code).scanTokens()
        tokens.forEach(::println)
    }

    fun error(line: Int, message: String) {
        report(line, "", message)
    }

    fun report(line: Int, where: String, message: String) {
        System.err.println("[line $line] Error$where: $message")
        hadError = true
    }
}

fun main(args: Array<String>) = Lox.main(args)
