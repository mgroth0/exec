package matt.exec.shutdown

import matt.lang.RUNTIME
import matt.exec.shutdown.ShutdownExecutor.taskList


fun duringShutdown(task: ()->Unit) = taskList.add(task)


private object ShutdownExecutor {
  val taskList = ArrayList<()->Unit>()

  init {
	RUNTIME.addShutdownHook(Thread {
	  taskList.forEach { it() }
	})
  }
}

