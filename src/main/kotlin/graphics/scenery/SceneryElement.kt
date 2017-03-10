package graphics.scenery

/**
* SceneryElement enum class, enumerates the elements that may be put in a [Hub]
*
* @author Ulrik Günther <hello@ulrik.is>
*/
enum class SceneryElement {
    /** Element for any renderers */
    Renderer,
    /** Element for any HMD input devices */
    HMDInput,
    /** Element for any window handles */
    Window,
    /** Element for OpenCL compute contexts */
    OpenCLContext,
    /** Element for input handling (ui-behaviour) */
    Input,
    /** Element for statistics */
    Statistics,
    /** Element for settings storage */
    Settings
}
