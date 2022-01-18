package graphics.scenery.proteins.chemistry

import org.biojava.nbio.structure.Bond
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Open class to store the configuration of a molecule.
 * [parent] perant of the node
 * [boundMolecules] children of the node
 * [bondOrder] order of the bond with which the node is connected to its parent, 0 if root
 */
open class BondTree(val element: String, val bondOrder: Int = 0, var id: String = "") {
    var boundMolecules = CopyOnWriteArrayList<BondTree>()
    var bondTreeParent: BondTree? = null

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
    /**
     * find a specific molecule bound to the molecule by its id
     */
    fun findByID(id: String): BondTree? {
        if(this.id == id) {
            return this
        }
        this.boundMolecules.forEach {
            if(it.id == id) {
                return it
            }
            if(it is BondTreeCycle) {
                it.cyclesAndChildren.forEach { cycleOrChild ->
                    cycleOrChild.forEach { substituent ->
                        val returnValue =  substituent.findByID(id)
                        if(returnValue != null) {
                            return  returnValue
                        }
                    }
                }
            }
            else {
                it.boundMolecules.forEach { child ->
                    val returnValue = child.findByID(id)
                    if(returnValue != null) {
                        return returnValue
                    }
                }
            }
        }
        return null
    }

    /**
     * remove a bounded molecule
     */
    fun removeByID(id: String) {
        this.addParentToChildren()
        var remainingChildren = mutableListOf(this)
        while(remainingChildren.isNotEmpty()) {
            if(remainingChildren.first().id == id && remainingChildren.first().bondTreeParent != null) {
                remainingChildren.first().bondTreeParent!!.removeFromChildren(id)
                return
            }
            else {
                remainingChildren.first().boundMolecules.forEach {
                    remainingChildren.add(it)
                }
            }
            remainingChildren.removeAt(0)
        }
    }

    /**
     * add a molecule at a given id
     */
    fun addAtID(id: String, newMolecule: BondTree) {
        this.boundMolecules.forEach {
            if(it.id == id) {
                it.addBoundMolecule(newMolecule)
            }
            if(it is BondTreeCycle) {
                it.cyclesAndChildren.forEach { cycleOrChild ->
                    cycleOrChild.forEach { substituent ->
                        substituent.findByID(id)?.addBoundMolecule(newMolecule)
                    }
                }
            }
            else {
                it.boundMolecules.forEach { child ->
                    child.findByID(id)?.addBoundMolecule(newMolecule)
                }
            }
        }
    }

    /**
     * find a respective id and change it to a new one
     */
    private fun findIdAndChangeIt(oldID: String, newID: String) {
        findByID(oldID)?.id = newID
    }

    /**
     * Changes the values of amino acids according to their appearance
     */
    fun renameAminoAcidIds(number: Int) {
        this.findIdAndChangeIt("N", "N$number")
        this.findIdAndChangeIt("Ca", "Ca$number")
        this.findIdAndChangeIt("OH", "OH$number")
        this.findIdAndChangeIt("HO", "HO$number")
        this.findIdAndChangeIt("HN", "HN$number")
        this.findIdAndChangeIt("HNB", "HNB$number")
        this.findIdAndChangeIt("C", "C$number")
    }

    private fun addParentToChildren() {
        this.boundMolecules.forEach {
            it.bondTreeParent = this
            it.addParentToChildren()
        }
    }

    private fun removeFromChildren(id: String) {
        this.boundMolecules.forEachIndexed { index, molecule ->
            if(molecule.id == id) {
                this.boundMolecules.removeAt(index)
            }
        }
    }
}
