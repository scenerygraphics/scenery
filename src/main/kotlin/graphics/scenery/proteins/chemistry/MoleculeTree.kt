package graphics.scenery.proteins.chemistry

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Open class to store the configuration of a molecule.
 * [parent] perant of the node
 * [boundMolecules] children of the node
 * [bondOrder] order of the bond with which the node is connected to its parent, 0 if root
 */
open class MoleculeTree(val element: String, val bondOrder: Int = 0, var id: String = "") {
    var boundMolecules = CopyOnWriteArrayList<MoleculeTree>()
    var moleculeTreeParent: MoleculeTree? = null

    /**
     * add a child
     */
    fun addMolecule(moleculeTree: MoleculeTree) {
        this.boundMolecules.add(moleculeTree)
    }

    /**
     * add hydrogen
     */
    fun addhydrogen(numberOfHydrogens: Int) {
        for (i in 0 until numberOfHydrogens) {
            val hydrogen = MoleculeTree("H", 1)
            this.boundMolecules.add(hydrogen)

        }
    }
    /**
     * find a specific molecule bound to the molecule by its id
     */
    fun findByID(id: String): MoleculeTree? {
        if(this.id == id) {
            return this
        }
        this.boundMolecules.forEach {
            if(it.id == id) {
                return it
            }
            it.boundMolecules.forEach { child ->
                val returnValue = child.findByID(id)
                if(returnValue != null) {
                    return returnValue
                }
            }

        }
        return null
    }

    /**
     * remove a bounded molecule
     */
    fun removeByID(id: String): MoleculeTree {
        val newChildren = CopyOnWriteArrayList<MoleculeTree>()
        this.boundMolecules.forEach {
            if(it.id != id) {
                newChildren.add(it.removeByID(id))
            }
        }
        this.boundMolecules = newChildren
        if(this is MoleculeTreeCycle) {
            val newCycles = ArrayList<ArrayList<MoleculeTree>>()
            this.cyclesAndChildren.forEachIndexed { index, cycle ->
                val newCycle = ArrayList<MoleculeTree>()
                cycle.forEach { substituent ->
                    if(substituent.id != id) {
                        newCycle.add(substituent.removeByID(id))
                    }
                }
                newCycles.add(newCycle)
            }
            this.cyclesAndChildren = newCycles
        }
        return this
    }

    /**
     * add a molecule at a given id
     */
    fun addAtID(id: String, newMolecule: MoleculeTree): MoleculeTree {
        val newChildren = CopyOnWriteArrayList<MoleculeTree>()
        if(this.id == id) {
            newChildren.add(newMolecule)
        }
        this.boundMolecules.forEach {
            newChildren.add(it.addAtID(id, newMolecule))
        }
        this.boundMolecules = newChildren
        if(this is MoleculeTreeCycle) {
            val newCycles = ArrayList<ArrayList<MoleculeTree>>()
            this.cyclesAndChildren.forEachIndexed { index, cycle ->
                val newCycle = ArrayList<MoleculeTree>()
                cycle.forEach { substituent ->
                    newCycle.add(substituent.addAtID(id, newMolecule))
                }
                newCycles.add(newCycle)
            }
            this.cyclesAndChildren = newCycles
        }
        return this
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
        if(this is MoleculeTreeCycle) {
            renameAminoIdCycle(number)
        }
        this.findIdAndChangeIt("N", "N$number")
        this.findIdAndChangeIt("Ca", "Ca$number")
        this.findIdAndChangeIt("OH", "OH$number")
        this.findIdAndChangeIt("HO", "HO$number")
        this.findIdAndChangeIt("HN", "HN$number")
        this.findIdAndChangeIt("HNB", "HNB$number")
        this.findIdAndChangeIt("C", "C$number")
    }
}
