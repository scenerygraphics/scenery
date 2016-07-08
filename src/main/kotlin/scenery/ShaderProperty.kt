package scenery

/**
 * ShaderProperty is an annotation that can be used for [Node] properties. Properties
 * marked as such will be submitted to the GLSL shader by the renderer.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ShaderProperty
