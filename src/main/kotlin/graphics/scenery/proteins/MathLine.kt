package graphics.scenery.proteins

import org.joml.Vector3f

/**
 * This class corresponds to a basic mathematical line, defined by a direction vector and a positional vector.
 * [direction] direction vector
 * [position] positional vector
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
data class MathLine(val direction: Vector3f, val position: Vector3f)
