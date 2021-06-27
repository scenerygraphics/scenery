package graphics.scenery

import org.biojava.nbio.structure.BondImpl

class AminoAcidsStickAndBall(val protein: Protein): Mesh("AminoAcids") {
    val structure = protein.structure
    val residues = structure.chains.flatMap { it.atomGroups }.filter { it.hasAminoAtoms() }
    val aminoList = AminoList()
    init {
        residues.forEach { group ->
            //TODO: store all bonds as instances of a cylinder and atoms as instances of an icosphere, position according to pdb
            aminoList.forEach { storedAA ->
                if(group.pdbName == storedAA.name) {

                }
            }
        }
    }
}
