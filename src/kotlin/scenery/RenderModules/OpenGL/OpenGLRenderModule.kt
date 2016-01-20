package scenery.RenderModules.OpenGL

/**
 * Created by ulrik on 20/01/16.
 */
interface OpenGLRenderModule {
    fun initialize(): Boolean
    fun draw()
}