package scenery

/**
* SceneryElement enum class, enumerates the elements that may be put in a [Hub]
*
* @author Ulrik Günther <hello@ulrik.is>
*/
enum class SceneryElement {
    /** Element for any renderers */
    RENDERER,
    /** Element for any HMD input devices */
    HMDINPUT,
    /** Element for any window handles */
    WINDOW,
    /** Element for OpenCL compute contexts */
    OPENCLCONTEXT,
    /** Element for input handling (ui-behaviour) */
    INPUT
}
