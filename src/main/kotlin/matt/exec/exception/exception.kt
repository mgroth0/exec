package matt.exec.exception

import matt.lang.model.file.FsFile
import matt.file.commons.LogContext
import matt.file.ext.getNextSubIndexedFileWork
import matt.log.profile.err.ExceptionResponse
import matt.log.profile.err.StructuredExceptionHandler
import matt.model.code.errreport.Report

class AppUncaughtExceptionHandler(
    private val logContext: LogContext,
    val extraShutdownHook: ((
        t: Thread, e: Throwable, shutdown: (() -> Unit)?, st: String, exceptionFile: FsFile
    ) -> ExceptionResponse),
    val shutdown: (() -> Unit)? = null,

    ) : StructuredExceptionHandler() {


    override fun handleException(
        t: Thread,
        e: Throwable,
        report: Report
    ): ExceptionResponse {
        println("handException1")
        report.print()
        println("handException2")

        val exceptionFolder by lazy {
            logContext.logFolder["exceptions"]
        }

        exceptionFolder.mkdirs()
        val exceptionFile by lazy {
            exceptionFolder.getNextSubIndexedFileWork("exception.txt", 100)()
        }

        println("writing error log to $exceptionFile")
        exceptionFolder.mkdirs()
        exceptionFile.text = report.text
        println("wrote error log to $exceptionFile")

        println("trying to show exception dialog")
        val response = extraShutdownHook(t, e, shutdown, report.text, exceptionFile)
        println("out of extraShutdownHook")
        e.printStackTrace()
        shutdown?.invoke()
        println("done possibly invoking shutdown in handleException")
        return response
    }


}





