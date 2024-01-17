package matt.exec.app

import matt.async.thread.TheThreadProvider
import matt.exec.app.initvalidator.startInitValidator
import matt.exec.exception.AppUncaughtExceptionHandler
import matt.file.commons.DATA_FOLDER
import matt.file.commons.LogContext
import matt.file.commons.hasFullFileAccess
import matt.file.commons.mattLogContext
import matt.lang.go
import matt.lang.model.file.FsFile
import matt.lang.shutdown.CancellableShutdownTask
import matt.lang.shutdown.MyShutdownContext
import matt.lang.shutdown.ShutdownExecutorImpl
import matt.lang.shutdown.preaper.ProcessReaper
import matt.lang.shutdown.preaper.ProcessReaperImpl
import matt.log.logger.Logger
import matt.log.profile.err.ExceptionResponse
import matt.model.code.report.Reporter
import matt.model.data.release.Version
import matt.rstruct.modId
import matt.shell.context.DefaultMacExecutionContext
import matt.shell.context.withReaper
import matt.shell.shell

context(ProcessReaper)
fun bluetoothIsOn() = "State: On" in shell("/usr/sbin/system_profiler", "SPBluetoothDataType")

val myVersion: Version by lazy {
    modId.version
}

fun contextForMainOnly() = DefaultMacExecutionContext.withReaper(ProcessReaperImpl(TheThreadProvider, ShutdownExecutorImpl()))

val myDataFolder by lazy { DATA_FOLDER[modId.appName] }

context(MyShutdownContext<CancellableShutdownTask>)
open class App(
    val requiresBluetooth: Boolean = false,
) {


    fun requireAccessToDownloadsAndDesktopFolders() {
        require(hasFullFileAccess()) {
            "file access issue"
        }
    }


    open fun extraShutdownHook(
        t: Thread,
        e: Throwable,
        shutdown: (App.() -> Unit)? = null,
        st: String,
        exceptionFile: FsFile
    ): ExceptionResponse {
        println("in extraShutdownHook")
        return ExceptionResponse.EXIT
    }


    protected fun main(
        shutdown: (App.() -> Unit)? = null,
        preFX: (App.() -> Unit)? = null,
        cfg: (() -> Unit)? = null,
        logContext: LogContext = mattLogContext,
        t: Reporter? = null,
        enableExceptionAndShutdownHandlers: Boolean = true
    ) {
        val processReaper = ProcessReaperImpl(TheThreadProvider, this@MyShutdownContext)
        with(processReaper) {
            (t as? Logger)?.info("Kotlin Version = ${KotlinVersion.CURRENT}")
            if (requiresBluetooth) {
                require(bluetoothIsOn()) { "please turn on bluetooth" }
            }
            cfg?.go { it.invoke() }
            startInitValidator()

            if (enableExceptionAndShutdownHandlers) {
                shutdown?.go {
                    duringShutdown {
                        it.invoke(this@App)
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
            preFX?.invoke(this@App)
        }
    }

//    val port by lazy {
//        val p = when (modId.appName) {
//            "task"       -> PortRegistry.task
//            "top"        -> PortRegistry.top
//            "notify"     -> PortRegistry.notify
//            "launch"     -> PortRegistry.launch
//            "brainstorm" -> PortRegistry.brainstorm
//            "kjg"        -> PortRegistry.kjg
//            "pdf"        -> PortRegistry.pdf
//            "spotify"    -> PortRegistry.spotify
//            "fxgui"      -> PortRegistry.omniFxGui
//            else         -> error("need to configure port for ${modId.appName}")
//        }
//        Port(p)
//    }

}






