package graphics.scenery

import org.joml.Vector3f

data class ChemicalElement(val atomicNumber: Int,
                           val symbol: String,
                           val name: String,
                           val atomicMass: Float,
                           val color: Vector3f,
                           val electronConfiguration: String,
                           val electroNegativity: Float,
                           val atomicRadius: Float,
                           val ionizationEnergy: Float,
                           val electronAffinity: Float,
                           val oxidationStates: String,
                           val standardState: String,
                           val meltingPoint: Float,
                           val boilingPoint: Float,
                           val density: Float,
                           val groupBlock: String,
                           val yearDiscovered: String)

