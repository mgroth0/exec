package matt.exec.app

import matt.auto.exception.MyDefaultUncaughtExceptionHandler
import matt.auto.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse
import matt.auto.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse.EXIT
import matt.auto.interapp.ActionServer
import matt.file.MFile
import matt.file.commons.DATA_FOLDER
import matt.file.commons.VERSION_TXT_FILE_NAME
import matt.kjlib.socket.port.Port
import matt.klib.lang.go
import matt.klib.lang.resourceTxt
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

open class App(
  val args: Array<String>
) {

  companion object {
	protected var flowApp: App? = null
  }

  init {
	flowApp = this
  }


  open fun extraShutdownHook(
	t: Thread,
	e: Throwable,
	shutdown: (App.()->Unit)? = null,
	st: String,
	exceptionFile: MFile
  ): ExceptionResponse {
	println("in extraShutdownHook")
	return EXIT
  }


  protected fun main(
	//	altAppInterfaceParam: ((InterAppMessage)->ActionResult?)? = null,
	socketServer: ActionServer? = null,
	shutdown: (App.()->Unit)? = null,
	prefx: (App.()->Unit)? = null,
	cfg: (()->Unit)? = null,

	) {
	cfg?.go { it.invoke() }
	/*thread { if (!testProtoTypeSucceeded()) err("bad") }*/
	InitValidator::class.subclasses().forEach { validator ->
	  require(validator.hasAnnotation<NoArgConstructor>()) { "Validators should have @NoArgConstructor" }
	  require(validator.createInstance().validate()) {
		"$validator did not pass"
	  }
	  val refAnnos = ValidatedOnInit::class.annotatedKTypes()
		.map { it.findAnnotation<ValidatedOnInit>() }
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
			t = t,
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
	socketServer?.coreLoop(threaded = true)
	prefx?.invoke(this)
  }
}


interface InitValidator {
  fun validate(): Boolean
}

annotation class ValidatedOnInit(val by: KClass<out InitValidator>)

class AppServer(
  messageHandler: ((InterAppMessage)->ActionResult?)? = null
): ActionServer(
  Port(appName),
  messageHandler = messageHandler
)