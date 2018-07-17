package graphics.scenery

import graphics.scenery.backends.Shaders

/**
 * This class stores paths to GLSL shader files to be used for rendering preferentially,
 * as well as any parameters that are then expanded by the GLSL precompiler.
 *
 * @param[shaders]: The list of custom shaders to use as material
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ShaderMaterial(var shaders: Shaders) : Material() {
    companion object {
//        fun fromFiles(files: Array<String>): ShaderMaterial {
//            return ShaderMaterial(Shaders.ShadersFromFiles(files))
//        }

        fun fromFiles(vararg files: String): ShaderMaterial {
            return ShaderMaterial(Shaders.ShadersFromFiles(files.toList().toTypedArray()))
        }

        fun fromClass(clazz: Class<*>): ShaderMaterial {
            return ShaderMaterial(Shaders.ShadersFromClassName(clazz))
        }
    }
}
