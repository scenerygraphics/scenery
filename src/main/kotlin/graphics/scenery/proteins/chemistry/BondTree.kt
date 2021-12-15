package graphics.scenery.proteins.chemistry

open class BondTree(val molecule: Molecule, val children: ArrayList<BondTree>, val parent: BondTree?) {
    fun isPartOfACycle(): Boolean {
        //TODO: implement
    }
}
