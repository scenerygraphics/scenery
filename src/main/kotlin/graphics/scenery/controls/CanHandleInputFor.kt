package graphics.scenery.controls

import kotlin.reflect.KClass

/**
 * Annotation class for [MouseAndKeyHandlerBase] classes that declare to handle a specific type of [SceneryWindow].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CanHandleInputFor(val windowTypes: Array<KClass<*>>)
