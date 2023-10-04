package matt.exec.app.initvalidator

import matt.async.thread.daemon
import matt.exec.app.InitValidator
import matt.exec.app.ValidatedOnInit
import matt.lang.require.requireOne
import matt.reflect.NoArgConstructor
import matt.reflect.scan.annotatedMattKTypes
import matt.reflect.scan.mattSubClasses
import matt.reflect.scan.systemScope
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation


internal fun startInitValidator() {
    daemon(name = "initValidator") {
        with(systemScope(includePlatformClassloader=false).usingClassGraph()) {
            InitValidator::class.mattSubClasses().forEach { validator ->
                require(validator.hasAnnotation<NoArgConstructor>()) {
                    "Validators should have @NoArgConstructor, $validator does not"
                }
                require(validator.createInstance().validate()) {
                    "$validator did not pass"
                }
                val refAnnos =
                    ValidatedOnInit::class.annotatedMattKTypes().map { it.findAnnotation<ValidatedOnInit>() }
                        .filter { it!!.by == validator }
                requireOne(refAnnos.size) {
                    "please mark with a @ValidatedOnInit who is validated by the validator $validator"
                }
            }
        }
    }

}