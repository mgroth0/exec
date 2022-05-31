package matt.exec.app

//import com.beust.klaxon.Klaxon
//import matt.reflect.NoArgConstructor
//import matt.reflect.testProtoTypeSucceeded
import matt.exec.exception.MyDefaultUncaughtExceptionHandler
import matt.exec.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse
import matt.exec.exception.MyDefaultUncaughtExceptionHandler.ExceptionResponse.EXIT
import matt.exec.interapp.InterAppListener
import matt.kjlib.lang.jlang.resourceTxt
import matt.kjlib.shutdown.beforeShutdown
import matt.kjlib.socket.port
import matt.klib.commons.DATA_FOLDER
import matt.klib.commons.get
import matt.klib.file.MFile
import matt.klib.lang.go
import matt.reflect.NoArgConstructor
import matt.reflect.annotatedKTypes
import matt.reflect.subclasses

import kotlin.concurrent.thread
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

val appName by lazy { resourceTxt("matt/appname.txt")!! }
val myDataFolder = DATA_FOLDER[appName]

open class App(
  val args: Array<String>
) {

  companion object {
	protected var flow_app: App? = null
  }

  var altAppInterface: Pair<String, Map<String, App.(String)->Unit>>? = null

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
	return EXIT
  }

  protected fun main(
	altAppInterfaceParam: Map<String, App.(String)->Unit>? = null,
	shutdown: (App.()->Unit)? = null,
	consumeShutdown: (App.()->Unit)? = null,
	prefx: (App.()->Unit)? = null,
	cfg: (()->Unit)? = null
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

	shutdown?.go { beforeShutdown { it.invoke(this) } }
	require(listOfNotNull(shutdown, consumeShutdown).count() <= 1)
	/*this is dirty because it doesnt consume the shutdown unless its a gui window close event*/
	consumeShutdown?.go { beforeShutdown { it.invoke(this) } }

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
		ProcessBuilder(
		  "/bin/sh", "-c",
		  "lsof -t -i tcp:${port(nam)} | xargs kill"
		).start().waitFor()
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
