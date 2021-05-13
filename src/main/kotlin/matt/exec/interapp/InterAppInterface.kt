package matt.exec.interapp

import matt.auto.activateByPid
import matt.auto.interapp.InterAppInterface
import matt.auto.interapp.MY_INTER_APP_SEM
import matt.auto.interapp.Sender
import matt.auto.interapp.port
import matt.exec.app.MY_APP_NAME
import matt.json.lang.get
import matt.json.prim.parseJson
import matt.kjlib.byte.readWithTimeout
import matt.kjlib.commons.VAL_JSON
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import kotlin.system.exitProcess


const val SLEEP_PERIOD = 100.toLong() //ms
const val PRINT_PERIOD = 10_000 //ms

@Suppress("unused")
val I_PERIOD = (PRINT_PERIOD/SLEEP_PERIOD).toInt()


class InterAppListener(name: String, val actions: Map<String, (String)->Unit>) {
  private val prt = port(name)
  private val serverSocket = try {
	print("serving $prt ...")
	ServerSocket(prt).apply {
	  println("started")
	}
  } catch (e: BindException) {
	println("")
	println("port was $prt")
	print("checking lsof...")
	val s = ProcessBuilder(
	  "bash",
	  "lsof -t -i tcp:${prt}"
	).start().let { it.inputStream.bufferedReader().readText() + it.errorStream.bufferedReader().readText() }
	println(" $s")
	e.printStackTrace()
	exitProcess(1)
  }

  fun core_loop() {
	var continue_running = true
	val debugAllSocksPleaseDontClose = mutableListOf<Socket>()
	while (continue_running) {
	  val clientSocket = serverSocket.accept()
	  debugAllSocksPleaseDontClose.add(clientSocket)
	  val out = clientSocket.getOutputStream()
	  println("SOCKET_CHANNEL=${clientSocket.channel}")
	  val inReader = BufferedReader(
		InputStreamReader(clientSocket.getInputStream())
	  )
	  MY_INTER_APP_SEM.acquire()
	  val signal = inReader.readWithTimeout(2000)
	  if (signal.isNotBlank()) {
		println("signal: $signal")
		when (signal) {
		  "EXIT"     -> {
			println("got quit signal")
			continue_running = false
			clientSocket.close()
			debugAllSocksPleaseDontClose.remove(clientSocket)
		  }
		  "ACTIVATE" -> {
			println("got activate signal")
			val pid = ProcessHandle.current().pid()
			activateByPid(pid)
			clientSocket.close()
			debugAllSocksPleaseDontClose.remove(clientSocket)
		  }
		  "HERE!"    -> {
		  }
		  else       -> {
			val key = signal.substringBefore(":")
			val value = signal.substringAfter(":")
			println("other signal (length=${signal.length}) (key=$key,value=$value)")
			if (key == "ARE_YOU_RUNNING") {

			  out.write("Here!\r\n".encodeToByteArray())
			  out.flush()

			  println("told them that im here!")
			} else {
			  actions.forEach { action ->
				if (action.key == key) {
				  action.value(value)
				}
			  }
			}
		  }
		}
	  }
	  MY_INTER_APP_SEM.release()
	}
	println("Out of while loop, exiting")
  }
}




fun activateThisProcess() = activateByPid(ProcessHandle.current().pid())

@Suppress("unused")
private class Request(
  val return_address: String,
  val key: String
)


fun waitFor(l: ()->Boolean) {
  while (!l()) {
	Thread.sleep(WAIT_FOR_MS.toLong())
  }
}

@Suppress("unused")
fun waitFor(service: String, me: String) {
  println("waiting for ${service}...")
  waitFor { InterAppInterface[service].are_you_running(me) != null }
  println("for response from ${service}! moving on")
}



val WAIT_FOR_MS = VAL_JSON.parseJson().let {
  val theInt: Int = it["WAIT_FOR_MS"]!!
  theInt
}


@Suppress("FunctionName", "unused")
fun Sender.are_you_running() = are_you_running(MY_APP_NAME!!)