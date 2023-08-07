package matt.exec.test


import matt.exec.app.App
import matt.test.JupiterTestAssertions.assertRunsInOneMinute
import kotlin.test.Test

class ExecTests {
    @Test
    fun constructClasses() = assertRunsInOneMinute {
        App(arrayOf("a,b,c"))
    }
}