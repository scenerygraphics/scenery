package graphics.scenery.proteins.chemistry

/**
 * Interface for reordering the atoms in a molecule according to a specific chirality.
 */
interface Chirality {
    /**
     * Chirality is essential to determine the 3D structure of a molecule. Chirality occurs if four or more distinct
     * elements are bound to the same atom. This function is there to sort the respective elements in a list such that
     * the algorithm to compute the 3D structure gets the right input.
     */
    fun orderAtomPositions(bondtree: BondTree): BondTree
}
