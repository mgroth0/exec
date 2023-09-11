package matt.exec.app

import matt.exec.app.initvalidator.startInitValidator
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
import matt.model.code.report.Reporter
import matt.model.code.vals.portreg.PortRegistry
import matt.model.data.release.Version
import matt.rstruct.modID
import matt.shell.shell
import matt.socket.port.Port
import kotlin.reflect.KClass

fun bluetoothIsOn() = "State: On" in shell("/usr/sbin/system_profiler", "SPBluetoothDataType")

val myVersion: Version by lazy {


    /*Version(extractMdValue(mdText = resourceTxt(CHANGELIST_MD)!!, key = "VERSION")!!)*/


    modID.version


}


val myDataFolder by lazy { DATA_FOLDER[modID.appName] }

open class App<A : App<A>>(
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
        t: Thread,
        e: Throwable,
        shutdown: (App<*>.() -> Unit)? = null,
        st: String,
        exceptionFile: MFile
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
        enableExceptionAndShutdownHandlers: Boolean = true
    ) {
        (t as? Logger)?.info("Kotlin Version = ${KotlinVersion.CURRENT}")
        if (requiresBluetooth) {
            require(bluetoothIsOn()) { "please turn on bluetooth" }
        }
        cfg?.go { it.invoke() }
        startInitValidator()

        if (enableExceptionAndShutdownHandlers) {
            shutdown?.go {
                duringShutdown {
                    it.invoke(this)
                }
            }
            val exceptionHandler = AppUncaughtExceptionHandler(
                logContext = logContext,
                extraShutdownHook = { thr, e, sd, st, ef ->
                    this@App.extraShutdownHook(
                        t = thr, e = e, shutdown = {
                            sd?.invoke()
                        }, st = st, exceptionFile = ef
                    )
                },
                shutdown = {
                    shutdown?.invoke(this@App)
                },
            )

            Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
        }


        /*Thread.getAllStackTraces()
        Thread.getAllStackTraces().keys.forEach {
            val previous = it.uncaughtExceptionHandler
            if (previous == null || previous is ThreadGroup) {
                it.uncaughtExceptionHandler = exceptionHandler
            }
        }*/
        preFX?.invoke(this)
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
            "fxgui"      -> PortRegistry.omniFxGui
            else         -> error("need to configure port for ${modID.appName}")
        }
        Port(p)
    }

}


interface InitValidator {
    fun validate(): Boolean
}

annotation class ValidatedOnInit(val by: KClass<out InitValidator>)
