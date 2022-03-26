package matt.exec.cmd

import matt.exec.app.App
import matt.kjlib.str.taball
import matt.kjlib.whileTrue
import matt.klibexport.klibexport.go

class CommandLineApp(
    val welcomeMessage: String? = null,
    mainPrompt: String,
    args: Array<String> = arrayOf(),
    private val cfg: (() -> Unit)? = null,
    private val cmdDSL: CommandLineApp.() -> Unit
) : App(args) {
    var stayAlive = true
    fun start(
        shutdown: (App.() -> Unit)? = null,
        consumeShutdown: (App.() -> Unit)? = null,
    ) {
        welcomeMessage?.go { println(it) }
        main(
            shutdown = shutdown, consumeShutdown = consumeShutdown, cfg=cfg
        )
        cmdDSL.invoke(this)
        whileTrue { rootInputPoint.run() }
    }

    fun String.does(op: () -> Unit) {
        rootActions[this] = op
    }

    fun acceptAny(op: (String) -> Unit) {
        rootInputPoint.acceptAnyFun = op
    }

    private val rootActions = mutableMapOf<String, () -> Unit>()
    private val rootInputPoint = InputPoint(mainPrompt, rootActions)
}

val exitCommands = listOf("q", "exit", "quit")

class InputPoint(val prompt: String, val actions: Map<String, () -> Unit>) {
    var acceptAnyFun: ((String) -> Unit)? = null
    fun run(): Boolean {
        print(prompt)
        val command = readLine()!!
        if (command in exitCommands) {
            return false
        }
        if (acceptAnyFun != null) {
            require(actions.isEmpty())
            acceptAnyFun!!(command)
        } else {
            actions[command]?.invoke() ?: run {
                println("valid commands:")
                taball(actions.keys)
            }
        }
        return true
    }
}