package graphics.scenery.proteins.chemistry

import graphics.scenery.proteins.AminoAcid
import graphics.scenery.proteins.AminoList
import graphics.scenery.utils.LazyLogger

/**
 * BondTree specific for amino acids as stored in the AminoList
 */
class AminoAcidBondTree(val aminoAcid: AminoAcid, val aminoParent:AminoAcidBondTree? = null,
                        val aminoChildren: List<AminoAcidBondTree> = ArrayList(), val aminoBondOrder: Int):
    BondTree(aminoAcid, aminoParent, aminoChildren, aminoBondOrder){

        //The bond tree is calculated here.
        companion object AminoTreeCalculator {
            private val logger by LazyLogger()
            /**
             * Convenience method to return the amino acid bond tree from an iupac abbreviation (three letters)
             */
            fun fromAbbreviation(abbreviation: String): AminoAcidBondTree {
                val aminoList = AminoList()
                aminoList.forEach {
                    if (abbreviation == it.name) {
                        return fromAminoAcid(it)
                    }
                }
                logger.warn("Your amino acid abbreviation code was invalid, fallback to alanine")
                return(fromAminoAcid(aminoList[1]))
            }
            /**
             * Convenience method to return the amino acid bond tree from an AminoAcid.
             */
            fun fromAminoAcid(aminoAcid: AminoAcid): AminoAcidBondTree {

            }
        }

}
