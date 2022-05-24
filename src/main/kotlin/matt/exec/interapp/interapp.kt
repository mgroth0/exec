package matt.exec.interapp

import matt.auto.activateByPid
import matt.exec.app.appName
import matt.json.lang.get
import matt.kbuild.VAL_JSON
import matt.kbuild.parseJson
import matt.kbuild.readWithTimeout
import matt.kbuild.socket.InterAppInterface
import matt.kbuild.socket.MY_INTER_APP_SEM
import matt.kbuild.socket.SingleSender
import matt.kbuild.socket.port
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import kotlin.system.exitProcess
import matt.kjlib.async.waitFor


const val SLEEP_PERIOD = 100.toLong() //ms
const val PRINT_PERIOD = 10_000 //ms

@Suppress("unused")
val I_PERIOD = (PRINT_PERIOD/SLEEP_PERIOD).toInt()

fun Socket.bufferedReader() = BufferedReader(
  InputStreamReader(getInputStream())
)


fun readSocketLines(
  port: Int,
): Sequence<String> {
  val socket = tryCreatingSocket(port)
  return sequence {
	val client = socket.accept()
	yieldAll(client.bufferedReader().lineSequence())
	client.close()
	socket.close()
  }
}

fun tryCreatingSocket(port: Int): ServerSocket {
  return try {
	print("serving $port ...")
	ServerSocket(port).apply {
	  println("started")
	}
  } catch (e: BindException) {
	println("")
	println("port was $port")
	print("checking lsof...")
	val s = ProcessBuilder(
	  "bash", "lsof -t -i tcp:${port}"
	).start().let { it.inputStream.bufferedReader().readText() + it.errorStream.bufferedReader().readText() }
	println(" $s")
	e.printStackTrace()
	exitProcess(1)
  }
}


class InterAppListener(name: String, val actions: Map<String, (String)->Unit>) {
  private val prt = port(name)
  private val serverSocket = tryCreatingSocket(prt)

  fun coreLoop() {
	var continueRunning = true
	val debugAllSocksPleaseDontClose = mutableListOf<Socket>()
	while (continueRunning) {
	  val clientSocket = serverSocket.accept()
	  debugAllSocksPleaseDontClose.add(clientSocket)
	  val out = clientSocket.getOutputStream()
	  println("SOCKET_CHANNEL=${clientSocket.channel}")
	  MY_INTER_APP_SEM.acquire()
	  val signal = clientSocket.bufferedReader().readWithTimeout(2000).trim()
	  if (signal.isBlank()) {
		println("signal is blank...")
	  }
	  if (signal.isNotBlank()) {
		println("signal: $signal")
		when (signal) {
		  "EXIT"     -> {
			println("got quit signal")
			continueRunning = false
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
		  "HERE!"    -> Unit
		  else       -> {
			val key = signal.substringBefore(":")
			val value = signal.substringAfter(":")
			println("other signal (length=${signal.length}) (key=$key,value=$value)")
			if (key == "ARE_YOU_RUNNING") {

			  out.write("Here!\r\n".encodeToByteArray())
			  out.flush()

			  println("told them that im here!")
			} else {
			  val action = actions.entries.firstOrNull { it.key == key }
			  if (action == null) {
				println("found no action with key \"$key\"")
			  } else {
				println("found action with key \"$key\". executing.")
				action.value(value)
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
  val returnAddress: String, val key: String
)

fun waitFor(l: ()->Boolean): Unit = waitFor(WAIT_FOR_MS.toLong(), l)

@Suppress("unused")
fun waitFor(service: String, me: String) {
  println("waiting for ${service}...")
  waitFor { InterAppInterface[service].areYouRunning(me) != null }
  println("for response from ${service}! moving on")
}


val WAIT_FOR_MS by lazy {
  VAL_JSON.parseJson().let {
	val theInt: Int = it["WAIT_FOR_MS"]!!
	theInt
  }
}

@Suppress("FunctionName", "unused")
fun SingleSender.areYouRunning() = areYouRunning(appName)