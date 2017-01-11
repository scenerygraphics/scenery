package graphics.scenery

/**
 * The NodeMetadata is a generic interface for metadata stored for a Node.
 * The interface only defines a list of [consumers], and is extended e.g. by
 * [OpenGLObjectState].
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface NodeMetadata {
    /** List of the names of consumers of this metadata. */
    val consumers: List<String>
}
