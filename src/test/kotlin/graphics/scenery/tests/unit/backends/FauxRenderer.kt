package graphics.scenery.tests.unit.backends

import graphics.scenery.*
import graphics.scenery.backends.RenderConfigReader
import graphics.scenery.backends.Renderer
import graphics.scenery.backends.SceneryWindow
import graphics.scenery.utils.SceneryPanel

/**
 * Faux renderer class used for testing only.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class FauxRenderer(override var hub: Hub?, var scene: Scene, preparedWindow: SceneryWindow? = null) : Renderer() {
    /**
     * Initializes scene and contents
     */
    override fun initializeScene() {
        logger.info("Initialising scene")
    }

    /**
     * Renders the scene
     */
    override fun render(activeCamera: Camera, sceneNodes: List<Node>) {
        logger.info("Rendering")
    }

    /** Signals whether the current renderer should stop working and close all open windows. */
    override var shouldClose: Boolean = false
    /** Signals whether the renderer is done initialiasing and can start with scene initialisation and rendering. */
    override var initialized: Boolean = false
    /** Signals whether a first image has been drawn. */
    override var firstImageReady: Boolean = false
    /** [Settings] instance the renderer is using. */
    override var settings: Settings = Settings(hub)
    /** [SceneryWindow] the renderer is drawing to. */
    override var window: SceneryWindow = preparedWindow ?: SceneryWindow.HeadlessWindow()
    /** A [SceneryPanel] the renderer might be embedded in. */
    override var embedIn: SceneryPanel? = null
    override fun close() {
        logger.info("Closing renderer")
    }

    /**
     * Takes a screenshot, and saves it as [filename].
     *
     * @param[filename] The filename where to save the screenshot.
     */
    override fun screenshot(filename: String, overwrite: Boolean) {
        logger.info("Wrote screenshot to $filename, overwrite: $overwrite")
    }

    /**
     * Reshapes the window to the given sizes.
     *
     * @param[newWidth] The new width of the window.
     * @param[newHeight] The new height of the window.
     */
    override fun reshape(newWidth: Int, newHeight: Int) {
        window.width = newWidth
        window.height = newHeight
    }

    /**
     * Sets the rendering quality, if the loaded renderer config file supports it.
     *
     * @param[quality] The [RenderConfigReader.RenderingQuality] to be set.
     */
    override fun setRenderingQuality(quality: RenderConfigReader.RenderingQuality) {
        logger.info("Set rendering quality to $quality")
    }

    /**
     * Activate or deactivate push-based rendering mode (render only on scene changes
     * or input events). Push mode is activated if [pushMode] is true.
     */
    override var pushMode: Boolean = false
    /**
     * Whether the renderer manages it's own main loop. If false, [graphics.scenery.SceneryBase] will take
     * care of the rendering loop inside its main loop.
     */
    override val managesRenderLoop: Boolean = false
    /** Total time taken for the last frame (in milliseconds). */
    override var lastFrameTime: Float = 1.0f
    /** The file to read the [RenderConfigReader.RenderConfig] from. */
    override var renderConfigFile: String = ""

    private var recordMovie = false
    override fun recordMovie(filename: String, overwrite: Boolean) {
        if(recordMovie) {
            logger.info("Recording movie to $filename")
            recordMovie = true
        } else {
            logger.info("Stopped recording movie")
        }
    }
}
