package matt.exec.cmd

import matt.exec.app.App
import matt.exec.cmd.CommandLineApp.Companion.exitCommands
import matt.lang.go
import matt.lang.require.requireEmpty
import matt.lang.whileTrue
import matt.log.taball

class CommandLineApp(
    val welcomeMessage: String? = null,
    mainPrompt: String,
    args: Array<String> = arrayOf(),
    private val cfg: (() -> Unit)? = null,
    private val cmdDSL: CommandLineApp.() -> Unit
) : App<CommandLineApp>(args) {

    fun start(
        shutdown: (App<*>.() -> Unit)? = null,
    ) {
        welcomeMessage?.go { println(it) }
        main(
            shutdown = shutdown, cfg = cfg
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

    companion object {
        val exitCommands = listOf("q", "exit", "quit")
    }
}


class InputPoint(
    private val prompt: String,
    private val actions: Map<String, () -> Unit>
) {
    var acceptAnyFun: ((String) -> Unit)? = null
    fun run(): Boolean {
        print(prompt)
        val command = readLine()!!
        if (command in exitCommands) return false
        if (acceptAnyFun != null) {
            requireEmpty(actions)
            acceptAnyFun!!(command)
        } else {
            actions[command]?.invoke() ?: run {
                taball("valid commands:", actions.keys + exitCommands)
            }
        }
        return true
    }
}