package graphics.scenery.proteins

import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.secstruc.SecStrucType
import org.joml.Vector3f

/**
 * data class for the GuidePoints.
 * @param [finalPoint] the point in world space of this guidepoint
 * @param[cVec] see RibbonCalculation
 * @param[dVec] see RibbonCalculation
 * @param[offset] see RibbonCalculation
 * @param[widthFactor] see RibbonCalculation
 * @param[prevResidue] the residue behind the guidePoint
 * @param[nextResidue] the residue in front of the guidePoint
 * @param[type] the type of the Secondary Structure the nextResidue is assigned to
 * @param[ssLength] stands for the numbers of following residues which are elements of the
 * same secondary structure (counting down to 1).
 *
 * @author  Justin Buerger <burger@mpi-cbg.de>
 */
data class GuidePoint(val finalPoint: Vector3f, val cVec: Vector3f, val dVec: Vector3f,
                      val offset: Float = 0f, var widthFactor: Float = 0f,
                      val prevResidue: Group?, val nextResidue: Group?, var type: SecStrucType, var ssLength: Int)
