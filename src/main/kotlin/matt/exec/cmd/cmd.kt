package matt.exec.cmd

import matt.exec.app.App
import matt.kjlib.str.taball
import matt.kjlib.whileTrue

class CommandLineApp(
  mainPrompt: String,
  args: Array<String> = arrayOf(),
  private val cmdDSL: CommandLineApp.()->Unit
): App(args) {
  var stayAlive = true
  fun start(
	shutdown: (App.()->Unit)? = null,
	consumeShutdown: (App.()->Unit)? = null,
  ) {
	main(
	  shutdown = shutdown, consumeShutdown = consumeShutdown
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
}

val exitCommands = listOf("q", "exit", "quit")

class InputPoint(val prompt: String, val actions: Map<String, ()->Unit>) {
  var acceptAnyFun: ((String)->Unit)? = null
  fun run(): Boolean {
	println(prompt)
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