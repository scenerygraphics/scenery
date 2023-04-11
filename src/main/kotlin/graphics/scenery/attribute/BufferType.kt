package graphics.scenery.attribute

/**
 * Enum to identify the BufferType inside the HashMap of [Buffers]
 * Containing VertexBuffer elements: [Position], [Color], [UVCoordinates], [Normals]
 * Containing IndexBuffer: [Index]
 * Containing SSBO: [ShaderStorage]
 */

enum class BufferType {
    //We can either do this as enum, but the user then must add to it here, for example for colors or custom stuff -> it also needs to be added inside the delegate
    // Buffers interface and be added to the setter override to cast the bytebuffer to the correct type -> maybe have a unordered map that maps a type to
    // a lambda which gets called in the setter, containing the type and casting ?
    /**
     * VertexBuffer elements
     */
    Position, UVCoordinates, Normals, Color,

    /**
     * IndexBuffer
     */
    Index,

    /**
     * ShaderStorageBuffer
     */
    ShaderStorage
}
