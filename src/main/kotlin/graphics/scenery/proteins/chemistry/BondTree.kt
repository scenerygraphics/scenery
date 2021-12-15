package graphics.scenery.proteins.chemistry

/**
 * Open class to store the configuration of a molecule.
 * [molecule] molecule of which a BondTree is to be configured
 * [parent] perant of the node
 * [children] children of the node
 * [bondOrder] order of the bond with which the node is connected to its parent, 0 if parent is null
 */
open class BondTree(val molecule: Molecule, val parent: BondTree?, val children: List<BondTree>, val bondOrder: Int = 0) {
    var isPartOfACycle = false
    init {
        checkIfPartOfACycle(this)
    }
    /**
     * It is necessary to know whether an atom is part of a cycle or not to calculate the positions correctly
     */
    fun checkIfPartOfACycle(root: BondTree) {
        root.children.forEach {
            if(it.children.contains(this)) {
                isPartOfACycle = true
            }
            checkIfPartOfACycle(it)
        }
    }
}
