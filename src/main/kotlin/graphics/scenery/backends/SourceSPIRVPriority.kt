package graphics.scenery.backends

/**
 * Enums to indicate whether the source code or SPIRV bytecode in a [ShaderPackage]
 * should take precedence.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
enum class SourceSPIRVPriority { SourcePriority, SPIRVPriority }
