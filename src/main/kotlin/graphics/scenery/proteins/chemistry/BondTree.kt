package graphics.scenery.proteins.chemistry

/**
 * Open class to store the configuration of a molecule.
 * [parent] perant of the node
 * [boundMolecules] children of the node
 * [bondOrder] order of the bond with which the node is connected to its parent, 0 if root
 */
open class BondTree(val element: String, val bondOrder: Int = 0, val id: String = "") {
    var boundMolecules = mutableListOf<BondTree>()

    /**
     * add a child
     */
    fun addBoundMolecule(bondTree: BondTree) {
        this.boundMolecules.add(bondTree)
    }

    /**
     * add hydrogen
     */
    fun addhydrogen(numberOfHydrogens: Int) {
        for (i in 0 until numberOfHydrogens) {
            val hydrogen = BondTree("H", 1)
            this.boundMolecules.add(hydrogen)

        }
    }
}
