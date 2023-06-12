package matt.exec.app

import matt.async.thread.daemon
import matt.exec.exception.AppUncaughtExceptionHandler
import matt.file.MFile
import matt.file.commons.DATA_FOLDER
import matt.file.commons.LogContext
import matt.file.commons.hasFullFileAccess
import matt.file.commons.mattLogContext
import matt.lang.go
import matt.lang.shutdown.duringShutdown
import matt.log.logger.Logger
import matt.log.profile.err.ExceptionResponse
import matt.log.reporter.TracksTime
import matt.model.code.report.Reporter
import matt.model.code.valjson.PortRegistry
import matt.model.data.release.Version
import matt.model.op.prints.Prints
import matt.reflect.NoArgConstructor
import matt.reflect.reflections.annotatedMattKTypes
import matt.reflect.reflections.mattSubClasses
import matt.rstruct.modID
import matt.shell.shell
import matt.socket.port.Port
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

fun bluetoothIsOn() = "State: On" in shell("/usr/sbin/system_profiler", "SPBluetoothDataType")

val myVersion: Version by lazy {


    /*Version(extractMdValue(mdText = resourceTxt(CHANGELIST_MD)!!, key = "VERSION")!!)*/


    modID.version


}


val myDataFolder by lazy { DATA_FOLDER[modID.appName] }

open class App<A : App<A>>(
    val args: Array<String>,
    val requiresBluetooth: Boolean = false,
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
        t: Thread, e: Throwable, shutdown: (App<*>.() -> Unit)? = null, st: String, exceptionFile: MFile
    ): ExceptionResponse {
        println("in extraShutdownHook")
        return ExceptionResponse.EXIT
    }


    protected fun main(
        shutdown: (App<*>.() -> Unit)? = null,
        preFX: (App<*>.() -> Unit)? = null,
        cfg: (() -> Unit)? = null,
        logContext: LogContext = mattLogContext,
        t: Reporter? = null,
    ) {
        (t as? Logger)?.info("Kotlin Version = ${KotlinVersion.CURRENT}")
        (t as? TracksTime)?.toc("starting main")
        if (requiresBluetooth) {
            require(bluetoothIsOn()) { "please turn on bluetooth" }
        }
        cfg?.go { it.invoke() }    /*thread { if (!testProtoTypeSucceeded()) err("bad") }*/
        (t as? TracksTime)?.toc("did cfg")
        daemon(name = "initValidator") {
            InitValidator::class.mattSubClasses().forEach { validator ->
                require(validator.hasAnnotation<NoArgConstructor>()) { "Validators should have @NoArgConstructor" }
                require(validator.createInstance().validate()) {
                    "$validator did not pass"
                }
                val refAnnos = ValidatedOnInit::class.annotatedMattKTypes().map { it.findAnnotation<ValidatedOnInit>() }
                    .filter { it!!.by == validator }
                require(refAnnos.size == 1) {
                    "please mark with a @ValidatedOnInit who is validated by the validator $validator"
                }
            }
        }
        (t as? TracksTime)?.toc("started InitValidator")

        shutdown?.go {
            duringShutdown {
                (t as? Prints)?.println("invoking shutdown")
                it.invoke(this)
                (t as? Prints)?.println("invoked shutdown")
            }
        }
        (t as? TracksTime)?.toc("setup shutdown")

        val exceptionHandler = AppUncaughtExceptionHandler(
            logContext = logContext,
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

        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
        /*Thread.getAllStackTraces()
        Thread.getAllStackTraces().keys.forEach {
            val previous = it.uncaughtExceptionHandler
            if (previous == null || previous is ThreadGroup) {
                it.uncaughtExceptionHandler = exceptionHandler
            }
        }*/
        (t as? TracksTime)?.toc("setup exception handler")
        preFX?.invoke(this)
        (t as? TracksTime)?.toc("ran pre-fx")
    }

    val port by lazy {
        val p = when (modID.appName) {
            "task"       -> PortRegistry.task
            "top"        -> PortRegistry.top
            "notify"     -> PortRegistry.notify
            "launch"     -> PortRegistry.launch
            "brainstorm" -> PortRegistry.brainstorm
            "kjg"        -> PortRegistry.kjg
            "pdf"        -> PortRegistry.pdf
            "spotify"    -> PortRegistry.spotify
            else         -> error("need to configure port for ${modID.appName}")
        }
        Port(p)
    }

}


interface InitValidator {
    fun validate(): Boolean
}

annotation class ValidatedOnInit(val by: KClass<out InitValidator>)
