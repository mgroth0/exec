package matt.exec.app

import matt.auto.exception.MyDefaultUncaughtExceptionHandler
import matt.auto.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse
import matt.auto.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse.EXIT
import matt.auto.interapp.InterAppListener
import matt.auto.interapp.InterAppResult
import matt.file.MFile
import matt.file.commons.DATA_FOLDER
import matt.file.commons.VERSION_TXT_FILE_NAME
import matt.kjlib.shell.Port
import matt.kjlib.socket.port
import matt.klib.lang.go
import matt.klib.lang.resourceTxt
import matt.klib.release.Version
import matt.klib.shutdown.beforeShutdown
import matt.reflect.NoArgConstructor
import matt.reflect.annotatedKTypes
import matt.reflect.subclasses
import kotlin.concurrent.thread
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
	protected var flow_app: App? = null
  }

  var altAppInterface: Pair<String, Map<String, App.(String)->InterAppResult>>? = null

  init {
	flow_app = this
  }


  open fun extraShutdownHook(
	t: Thread,
	e: Throwable,
	shutdown: (App.()->Unit)? = null,
	consumeShutdown: (App.()->Unit)? = null,
	st: String,
	exceptionFile: MFile
  ): ExceptionResponse {
	println("in extraShutdownHook")
	return EXIT
  }


  protected fun main(
	altAppInterfaceParam: Map<String, App.(String)->InterAppResult>? = null,
	shutdown: (App.()->Unit)? = null,
	consumeShutdown: (App.()->Unit)? = null,
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

	shutdown?.go { beforeShutdown {
	  println("invoking shutdown")
	  it.invoke(this)
	  println("invoked shutdown")
	} }
	require(listOfNotNull(shutdown, consumeShutdown).count() <= 1)
	/*this is dirty because it doesnt consume the shutdown unless its a gui window close event*/
	consumeShutdown?.go { beforeShutdown {
	  println("invoking consumeShutdown")
	  it.invoke(this)
	  println("invoked consumeShutdown")
	} }

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
		  consumeShutdown?.invoke(this@App)
		},
	  )
	)


	if (altAppInterfaceParam != null) {
	  this.altAppInterface = appName to altAppInterfaceParam
	}
	if (flow_app!!.altAppInterface != null) {
	  val nam = flow_app!!.altAppInterface!!.first
	  thread(isDaemon = true) {


		Port(port(nam)).processes().forEach { it.kill() }


		/*ProcessBuilder(
		  "/bin/sh", "-c",
		  "lsof -t -i tcp:${port(nam)} | xargs kill"
		).start().waitFor()*/
		InterAppListener(
		  name = nam,
		  actions = flow_app!!.altAppInterface!!.second.map {
			val key = it.key
			val handler = it.value
			key to { arg: String ->
			  flow_app!!.run { handler(arg) }
			}
		  }.toMap()
		).coreLoop()
	  }
	}
	prefx?.invoke(this)
  }
}


interface InitValidator {
  fun validate(): Boolean
}

annotation class ValidatedOnInit(val by: KClass<out InitValidator>)
