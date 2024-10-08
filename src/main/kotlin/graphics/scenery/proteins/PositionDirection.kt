package graphics.scenery.proteins

import org.joml.Vector3f

/**
 * This class corresponds to a basic mathematical line, defined by a direction vector and a positional vector.
 * [position] positional vector
 * [direction] direction vector
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
data class PositionDirection(val position: Vector3f, val direction: Vector3f)
