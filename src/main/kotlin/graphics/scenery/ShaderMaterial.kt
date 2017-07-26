package graphics.scenery

/**
 * This class stores paths to GLSL shader files to be used for rendering preferentially,
 * as well as any parameters that are then expanded by the GLSL precompiler.
 *
 * @param[shaders]: The list of custom shaders to use as material
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ShaderMaterial(var shaders: List<String>) : Material()
