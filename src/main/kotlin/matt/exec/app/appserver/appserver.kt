package matt.exec.app.appserver

import matt.auto.activateThisProcess
import matt.auto.interapp.ActionServer
import matt.exec.app.App
import matt.exec.app.appName
import matt.kjlib.socket.port.Port
import matt.stream.message.ACTIVATE
import matt.stream.message.ActionResult
import matt.stream.message.InterAppMessage
import matt.stream.message.NOTHING_TO_SEND


class AppServer<A: App<A>>(
  app: A,
  messageHandler: (A.(InterAppMessage)->ActionResult?)? = null
): ActionServer(
  Port(appName),
  messageHandler = { x: InterAppMessage ->
	when (x) {
	  is ACTIVATE -> {
		activateThisProcess()
		NOTHING_TO_SEND
	  }

	  else        -> messageHandler?.invoke(app, x)
	}
  }
)