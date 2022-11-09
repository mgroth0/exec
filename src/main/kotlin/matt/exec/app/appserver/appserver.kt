package matt.exec.app.appserver

import matt.auto.activateThisProcess
import matt.auto.interapp.ActionServer
import matt.exec.app.App
import matt.kjlib.socket.port.Port
import matt.model.message.ACTIVATE
import matt.model.message.ActionResult
import matt.model.message.InterAppMessage
import matt.model.message.NOTHING_TO_SEND
import matt.mstruct.rstruct.appName


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