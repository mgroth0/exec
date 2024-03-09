package matt.exec.app.daemon

import matt.exec.app.App
import matt.lang.shutdown.TypicalShutdownContext


context(TypicalShutdownContext)
class Daemon : App(
    requiresBluetooth = false
)
