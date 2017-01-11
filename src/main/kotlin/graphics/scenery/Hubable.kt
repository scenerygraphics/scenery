package graphics.scenery

/**
 * Simple interface for classes that may become part of a [Hub]
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
interface Hubable {
    /** The Hub this instance belongs to */
    var hub: Hub?
}
