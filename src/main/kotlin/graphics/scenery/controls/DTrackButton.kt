package graphics.scenery.controls

/**
 * Class to encapsulate the buttons on a DTrack flystick.
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
enum class DTrackButton(val index: Int) {
    Trigger(0),
    Left(3),
    Center(2),
    Right(1),
}
