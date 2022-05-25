package matt.exec.exception

import matt.auto.SublimeText
import matt.exec.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse.EXIT
import matt.exec.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse.IGNORE
import matt.kbuild.FLOW_FOLDER
import matt.kjlib.byte.MemReport
import matt.kjlib.file.get
import matt.kjlib.file.getNextAndClearWhenMoreThan
import matt.kjlib.file.text
import java.io.File
import java.lang.Thread.UncaughtExceptionHandler
import kotlin.random.Random.Default.nextDouble
import kotlin.system.exitProcess

val runtimeID = nextDouble()

class MyDefaultUncaughtExceptionHandler(
  val extraShutdownHook: ((
	t: Thread,
	e: Throwable,
	shutdown: (()->Unit)?,
	st: String,
	exception_file: File
  )->ExceptionResponse),
  val shutdown: (()->Unit)? = null,
): UncaughtExceptionHandler {
  override fun uncaughtException(t: Thread?, e: Throwable?) {

	  e?.printStackTrace()

	/*dont delete until I find source of disappearing exceptions*/
	println("in uncaughtException for $e")

	val exceptionFolder = FLOW_FOLDER["log"]["exceptions"]
	exceptionFolder.mkdirs()


	val exceptionFile = exceptionFolder["exception.txt"].getNextAndClearWhenMoreThan(100)



	require(e != null) {
	  "I didn't know throwable could be null"
	}
	require(t != null) {
	  "I didn't know thread could be null"
	}
	println("got exception: ${e::class.simpleName}: ${e.message}")
	var ee = e
	while (ee!!.cause != null) {
	  println("caused by: ${ee.cause!!::class.simpleName}: ${ee.cause!!.message}")
	  ee = ee.cause
	}
	val st = e.stackTraceToString()

	val bugReport = "runtimeID:${runtimeID}\n\n${MemReport()}\n\n${st}"

	e.printStackTrace()
	exceptionFile.text = bugReport
	println("trying to show exception dialog")
	val response = extraShutdownHook(t, e, shutdown, bugReport, exceptionFile)

	e.printStackTrace()

	// FIXME: exception_file.openInIntelliJ()
	SublimeText.open(exceptionFile)
	shutdown?.invoke()
	when (response) {
	  EXIT   -> {
		println("ok really exiting")
		exitProcess(1)
	  }
	  IGNORE -> {
		println("ignoring that exception")
	  }
	}
  }

  enum class ExceptionResponse { EXIT, IGNORE }
}
