package matt.exec.cmd

import matt.exec.app.App
import matt.exec.cmd.CommandLineApp.Companion.exitCommands
import matt.kjlib.lang.whileTrue
import matt.kjlib.str.taball
import matt.klibexport.klibexport.go

class CommandLineApp(
  val welcomeMessage: String? = null,
  mainPrompt: String,
  args: Array<String> = arrayOf(),
  private val cfg: (()->Unit)? = null,
  private val cmdDSL: CommandLineApp.()->Unit
): App(args) {

  fun start(
	shutdown: (App.()->Unit)? = null,
	consumeShutdown: (App.()->Unit)? = null,
  ) {
	welcomeMessage?.go { println(it) }
	main(
	  shutdown = shutdown, consumeShutdown = consumeShutdown, cfg = cfg
	)
	cmdDSL.invoke(this)
	whileTrue { rootInputPoint.run() }
  }

  fun String.does(op: ()->Unit) {
	rootActions[this] = op
  }

  fun acceptAny(op: (String)->Unit) {
	rootInputPoint.acceptAnyFun = op
  }

  private val rootActions = mutableMapOf<String, ()->Unit>()
  private val rootInputPoint = InputPoint(mainPrompt, rootActions)

  companion object {
	val exitCommands = listOf("q", "exit", "quit")
  }
}


class InputPoint(
  private val prompt: String,
  private val actions: Map<String, ()->Unit>
) {
  var acceptAnyFun: ((String)->Unit)? = null
  fun run(): Boolean {
	print(prompt)
	val command = readLine()!!
	if (command in exitCommands) return false
	if (acceptAnyFun != null) {
	  require(actions.isEmpty())
	  acceptAnyFun!!(command)
	} else {
	  actions[command]?.invoke() ?: run {
		println("valid commands:")
		taball(actions.keys + exitCommands)
	  }
	}
	return true
  }
}