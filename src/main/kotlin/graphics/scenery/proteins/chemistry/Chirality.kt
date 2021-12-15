package graphics.scenery.proteins.chemistry

/**
 * Interface for reordering the atoms in a molecule according to a specific chirality.
 */
interface Chirality {
    fun orderAtomPositions(bondtree: BondTree): BondTree
}
