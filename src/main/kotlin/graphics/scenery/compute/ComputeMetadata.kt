package graphics.scenery.compute

import org.joml.Vector3i

/**
 * Enum class to define the invocation type of a computation.
 * Once will execute the computation once, and reset it's [ComputeMetadata.active] flag to false and keep it so.
 * Triggered will execute the computation if [ComputeMetadata.active] is true, and reset it.
 * Permanent will run the computation permanently.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
enum class InvocationType {
    Once,
    Triggered,
    Permanent
}

/**
 * Metadata for nodes executing compute shaders, for definition of [workSizes] (e.g., the image
 * size an image processing operation is working on) and [invocationType]. A computation will
 * only be executed if [active] is true.
 *
 * To determine the final group count in X/Y/Z, the components of [workSizes] are divided by the
 * local sizes defined in the compute shader.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
data class ComputeMetadata(
    val workSizes: Vector3i,
    val invocationType: InvocationType = InvocationType.Permanent,
    @Volatile var active: Boolean = true
)
