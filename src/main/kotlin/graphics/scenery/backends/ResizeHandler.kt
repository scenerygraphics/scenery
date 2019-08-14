package graphics.scenery.backends

/**
    Resize Handler interface

    @author Ulrik Guenther <hello@ulrik.is>
*/

interface ResizeHandler {
    /** Timestamp of the last resize */
    var lastResize: Long

    /** Last width of window or panel */
    var lastWidth: Int
    /** Last height of window or panel */
    var lastHeight: Int

    /** Function to query whether resizing is necessary */
    fun queryResize()
}
