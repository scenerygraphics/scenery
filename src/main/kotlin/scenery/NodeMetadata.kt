package scenery

/**
 * The NodeMetadata is a generic interface for metadata stored for a Node.
 * The interface only defines a list of [consumers], and is extended e.g. by
 * [OpenGLObjectState].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface NodeMetadata {
    val consumers: List<String>
}
