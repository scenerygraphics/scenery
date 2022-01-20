package graphics.scenery.proteins.chemistry

/**
 * Simplest form of chirality: R,S configuration.
 */
class RSchirality(val moleculeTree: MoleculeTree, val rs: String): Chirality {
    override fun orderAtomPositions(bondtree: MoleculeTree): MoleculeTree {
        TODO("Not yet implemented")
    }
}
