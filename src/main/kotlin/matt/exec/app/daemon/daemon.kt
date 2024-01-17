package matt.exec.app.daemon

import matt.exec.app.App


context(matt.lang.shutdown.MyShutdownContext<matt.lang.shutdown.CancellableShutdownTask>)
class Daemon : App(
    requiresBluetooth = false
)