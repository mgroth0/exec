package matt.exec.test


import matt.exec.app.App
import matt.exec.app.contextForMainOnly
import matt.exec.app.initvalidator.InitValidator
import matt.exec.app.initvalidator.ValidatedOnInit
import matt.exec.cmd.CommandLineApp
import matt.exec.cmd.InputPoint
import matt.exec.exception.AppUncaughtExceptionHandler
import matt.file.commons.logctx.LogContext
import matt.file.construct.toMFile
import matt.lang.model.file.MacFileSystem
import matt.log.profile.err.ExceptionResponse
import matt.test.Tests
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test

class ExecTests : Tests() {


    @Test fun instantiateClasses() {
        App()
        ValidatedOnInit(InitValidator::class)
        CommandLineApp("hello?", "hellooo?") {
        }
        InputPoint("hello", mapOf())
    }
    @Test
    fun instantiateMoreClasses(
        @TempDir tempDir: Path
    ) {
        AppUncaughtExceptionHandler(
            logContext = LogContext(tempDir.toMFile(MacFileSystem)),
            { _, _, _, _, _ ->
                ExceptionResponse.IGNORE
            }
        ) {
        }
    }

    @Test fun runFunctions() {
        contextForMainOnly() /*just testing it, ok!?*/
    }
}
