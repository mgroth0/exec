package matt.exec.interapp

import matt.klib.log.DefaultLogger
import matt.klib.log.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import matt.async.waitFor
import matt.auto.activateByPid
import matt.exec.app.appName
import matt.json.lang.get
import matt.json.parseJson
import matt.kjlib.socket.InterAppInterface
import matt.kjlib.socket.MY_INTER_APP_SEM
import matt.kjlib.socket.SingleSender
import matt.kjlib.socket.port
import matt.kjlib.socket.reader.SocketReader
import matt.kjlib.socket.reader.readTextBeforeTimeout
import matt.klib.commons.VAL_JSON
import matt.klib.lang.go
import java.net.BindException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.system.exitProcess


const val SLEEP_PERIOD = 100.toLong() //ms
const val PRINT_PERIOD = 10_000 //ms

@Suppress("unused")
val I_PERIOD = (PRINT_PERIOD/SLEEP_PERIOD).toInt()


fun ServerSocket.acceptOrTimeout(): Socket? {
  return try {
	accept()
  } catch (e: SocketTimeoutException) {
	null
  }
}

fun readSocketLines(
  port: Int,
  delayMS: Long = 100
) = flow {
  tryCreatingSocket(port).use { server ->
	server.soTimeout = delayMS.toInt()
	server.use {
	  while (!server.isClosed) {
		server.acceptOrTimeout()?.go { client ->
		  //		  println("emiting all")
		  val sReader = SocketReader(client)
		  do {
			val line = sReader.readLineOrSuspend(delayMS)
			emit(line)
		  } while (line != null)

		  //		  emitAll(
		  //			client.bufferedReader().lineFlow(delayMS = delayMS)
		  //		  )
		  //		  println("emitted all")
		} ?: delay(delayMS)
	  }
	}
  }
}

fun tryCreatingSocket(port: Int) = try {
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




class InterAppListener(
  prt: Int,
  val actions: Map<String, (String)->Unit>,
  val continueOp: InterAppListener.()->Boolean = { true },
  private val log: Logger = DefaultLogger
) {
  constructor(name: String, actions: Map<String, (String)->Unit>): this(port(name), actions)

  val serverSocket = tryCreatingSocket(prt)

  fun coreLoop() {
	log += "starting coreLoop"
	var continueRunning = true
	val debugAllSocksPleaseDontClose = mutableListOf<Socket>()

	/*necessary so the serverSocket.accept() doesn't block forever, which causes apps and the idea plugin to hang*/
	serverSocket.soTimeout = 100

	serverSocket.use {
	  log += "using serverSocket"
	  while (continueRunning && continueOp()) {
		val clientSocket = try {
		  serverSocket.accept()
		} catch (e: SocketTimeoutException) {
		  continue
		}
		debugAllSocksPleaseDontClose.add(clientSocket)
		val out = clientSocket.getOutputStream()
		log += ("SOCKET_CHANNEL=${clientSocket.channel}")
		MY_INTER_APP_SEM.acquire()
		log += "got sem"
		val signal = clientSocket.readTextBeforeTimeout(2000).trim()
		if (signal.isBlank()) {
		  log += ("signal is blank...")
		}
		if (signal.isNotBlank()) {
		  log += ("signal: $signal")
		  when (signal) {
			"EXIT"     -> {
			  log += ("got quit signal")
			  continueRunning = false
			  clientSocket.close()
			  debugAllSocksPleaseDontClose.remove(clientSocket)
			}

			"ACTIVATE" -> {
			  log += ("got activate signal")
			  val pid = ProcessHandle.current().pid()
			  activateByPid(pid)
			  clientSocket.close()
			  debugAllSocksPleaseDontClose.remove(clientSocket)
			}

			"HERE!"    -> Unit
			else       -> {
			  val key = signal.substringBefore(":")
			  val value = signal.substringAfter(":")
			  log += ("other signal (length=${signal.length}) (key=$key,value=$value)")
			  if (key == "ARE_YOU_RUNNING") {

				out.write("Here!\r\n".encodeToByteArray())
				out.flush()

				log += ("told them that im here!")
			  } else {
				val action = actions.entries.firstOrNull { it.key == key }
				if (action == null) {
				  log += ("found no action with key \"$key\"")
				} else {
				  log += ("found action with key \"$key\". executing.")
				  action.value(value)
				}
			  }
			}
		  }
		}
		MY_INTER_APP_SEM.release()
	  }
	  log += ("out of while loop, closing server socket")
	}
	log += ("Out of while loop, exiting")
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