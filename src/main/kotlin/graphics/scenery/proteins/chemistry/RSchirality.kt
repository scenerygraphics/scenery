package graphics.scenery.proteins.chemistry

/**
 * Simplest form of chirality: R,S configuration.
 */
class RSchirality(val bondTree: BondTree, val rs: String): Chirality {
    override fun orderAtomPositions(bondtree: BondTree): BondTree {
        TODO("Not yet implemented")
    }
}
