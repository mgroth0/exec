package matt.exec.test


import matt.exec.app.App
import matt.test.Tests
import kotlin.test.Test

class ExecTests : Tests() {
    @Test
    fun constructClasses() = assertRunsInOneMinute {
        App()
    }
}