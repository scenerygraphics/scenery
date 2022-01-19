package graphics.scenery.proteins.chemistry

class MoleculeTreeCycle(element: String, val cyclesAndChildren: List<List<MoleculeTree>>, bondOrder: Int, id: String = ""):
    MoleculeTree(element = element, bondOrder, id) {
        init {
            cyclesAndChildren.filter { it.size == 1 }.forEach {
                this.boundMolecules.add(it[0])
            }
        }
    }
