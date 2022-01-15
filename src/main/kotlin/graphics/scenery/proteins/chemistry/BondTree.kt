package graphics.scenery.proteins.chemistry

/**
 * Open class to store the configuration of a molecule.
 * [parent] perant of the node
 * [boundMolecules] children of the node
 * [bondOrder] order of the bond with which the node is connected to its parent, 0 if parent is null
 */
open class BondTree(val element: String, val bondOrder: Int = 0, val id: String = "") {
    var boundMolecules = mutableListOf<BondTree>()
    var isPartOfACycle = false
    val periodicTable = PeriodicTable()
    init {
        checkIfPartOfACycle(this)
    }
    /**
     * It is necessary to know whether an atom is part of a cycle or not to calculate the positions correctly
     */
    fun checkIfPartOfACycle(root: BondTree) {
        root.boundMolecules.forEach {
            if(it.boundMolecules.contains(this)) {
                isPartOfACycle = true
            }
            checkIfPartOfACycle(it)
        }
    }

    /**
     * Get the data for the respective chemical element
     */
    fun getDataForElement(): ChemicalElement {
        return periodicTable.findElementBySymbol(element)
    }

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
