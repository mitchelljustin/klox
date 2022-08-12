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

    private fun runFile(filename: String) {
        val code = Files.readString(Paths.get(filename), Charset.defaultCharset())
        runInterpreter(code)
        if (hadError) {
            exitProcess(65)
        }
    }

    private fun runPrompt() {
        while (true) {
            print(">> ")
            val line = readLine() ?: break
            runInterpreter(line)
            hadError = false
        }
    }

    private fun runInterpreter(code: String) {
        val tokens = Scanner(code).scan()
        println(tokens.joinToString(", "))
        val expr = Parser(tokens).parse() ?: return
        if (hadError) return
        println(expr.verbose())
        val result = Interpreter().interpret(expr)
        println("=> $result")
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
