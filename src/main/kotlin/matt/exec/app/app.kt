package matt.exec.app

import matt.async.thread.daemon
import matt.auto.exception.AppUncaughtExceptionHandler
import matt.exec.app.appserver.AppServer
import matt.file.MFile
import matt.file.commons.CHANGELIST_MD
import matt.file.commons.DATA_FOLDER
import matt.file.commons.hasFullFileAccess
import matt.lang.go
import matt.lang.resourceTxt
import matt.lang.shutdown.duringShutdown
import matt.log.profile.ExceptionResponse
import matt.log.profile.ExceptionResponse.EXIT
import matt.log.profile.Stopwatch
import matt.model.message.ActionResult
import matt.model.message.InterAppMessage
import matt.model.release.Version
import matt.model.tech.md.extractMdValue
import matt.reflect.NoArgConstructor
import matt.reflect.annotatedKTypes
import matt.reflect.subclasses
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

val appName by lazy { resourceTxt("matt/appname.txt")!! }
val myVersion: Version by lazy { Version(extractMdValue(mdText = resourceTxt(CHANGELIST_MD)!!, key = "VERSION")!!) }


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
	t: Stopwatch? = null
	) {
	t?.toc("starting main")
	cfg?.go { it.invoke() }    /*thread { if (!testProtoTypeSucceeded()) err("bad") }*/
	t?.toc("did cfg")
	daemon{
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
	}
	t?.toc("started InitValidator")

	shutdown?.go {
	  duringShutdown {
		println("invoking shutdown")
		it.invoke(this)
		println("invoked shutdown")
	  }
	}
	t?.toc("setup shutdown")
	Thread.setDefaultUncaughtExceptionHandler(
	  AppUncaughtExceptionHandler(
		extraShutdownHook = { thr, e, sd, st, ef ->
		  this@App.extraShutdownHook(
			t = thr,
			e = e,
			shutdown = {
			  sd?.invoke()
			},
			st = st,
			exceptionFile = ef
		  )
		},
		shutdown = {
		  shutdown?.invoke(this@App)
		},
	  )
	)
	t?.toc("setup exception handler")
	preFX?.invoke(this)
	t?.toc("ran pre-fx")
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
