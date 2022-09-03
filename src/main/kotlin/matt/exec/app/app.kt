package matt.exec.app

import matt.auto.exception.MyDefaultUncaughtExceptionHandler
import matt.auto.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse
import matt.auto.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse.EXIT
import matt.exec.app.appserver.AppServer
import matt.file.MFile
import matt.file.commons.DATA_FOLDER
import matt.file.commons.FILE_ACCESS_CHECK_FILE
import matt.file.commons.VERSION_TXT_FILE_NAME
import matt.file.commons.hasFullFileAccess
import matt.lang.go
import matt.lang.resourceTxt
import matt.klib.release.Version
import matt.klib.shutdown.duringShutdown
import matt.reflect.NoArgConstructor
import matt.reflect.annotatedKTypes
import matt.reflect.subclasses
import matt.stream.message.ActionResult
import matt.stream.message.InterAppMessage
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

val appName by lazy { resourceTxt("matt/appname.txt")!! }
val myVersion: Version by lazy { Version(resourceTxt(VERSION_TXT_FILE_NAME)!!) }


val myDataFolder = DATA_FOLDER[appName]

open class App<A: App<A>>(
  val args: Array<String>
) {

  companion object {
	protected var flowApp: App<*>? = null
  }

  init {
	flowApp = this
  }

  fun requireAccessToDownloadsAndDesktopFolders() {
	require(hasFullFileAccess()) {
	  "file access issue"
	}
  }


  open fun extraShutdownHook(
	t: Thread, e: Throwable, shutdown: (App<*>.()->Unit)? = null, st: String, exceptionFile: MFile
  ): ExceptionResponse {
	println("in extraShutdownHook")
	return EXIT
  }


  protected fun main(
	shutdown: (App<*>.()->Unit)? = null,
	preFX: (App<*>.()->Unit)? = null,
	cfg: (()->Unit)? = null,

	) {
	cfg?.go { it.invoke() }    /*thread { if (!testProtoTypeSucceeded()) err("bad") }*/
	InitValidator::class.subclasses().forEach { validator ->
	  require(validator.hasAnnotation<NoArgConstructor>()) { "Validators should have @NoArgConstructor" }
	  require(validator.createInstance().validate()) {
		"$validator did not pass"
	  }
	  val refAnnos = ValidatedOnInit::class.annotatedKTypes().map { it.findAnnotation<ValidatedOnInit>() }
		.filter { it!!.by == validator }
	  require(refAnnos.size == 1) {
		"please mark with a @ValidatedOnInit who is validated by the validator $validator"
	  }
	}

	shutdown?.go {
	  duringShutdown {
		println("invoking shutdown")
		it.invoke(this)
		println("invoked shutdown")
	  }
	}
	Thread.setDefaultUncaughtExceptionHandler(
	  MyDefaultUncaughtExceptionHandler(
		extraShutdownHook = { t, e, sd, st, ef ->
		  this@App.extraShutdownHook(
			t = t, e = e, shutdown = {
			  sd?.invoke()
			}, st = st, exceptionFile = ef
		  )
		},
		shutdown = {
		  shutdown?.invoke(this@App)
		},
	  )
	)
	preFX?.invoke(this)
  }

  fun socketServer(messageHandler: (A.(InterAppMessage)->ActionResult?)?) {
	println("making socketServer")
	@Suppress("UNCHECKED_CAST")
	val server = AppServer(this as A, messageHandler)
	println("got server")
	server.coreLoop(threaded = true)
	println("ran core loop")
  }

}


interface InitValidator {
  fun validate(): Boolean
}

annotation class ValidatedOnInit(val by: KClass<out InitValidator>)
