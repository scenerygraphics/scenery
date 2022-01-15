package graphics.scenery.proteins.chemistry

class BondTreeCycle(element: String, val cyclesAndChildren: List<List<BondTree>>, bondOrder: Int, id: String = ""):
    BondTree(element = element, bondOrder, id) {
        init {
            cyclesAndChildren.filter { it.size == 1 }.forEach {
                this.boundMolecules.add(it[0])
            }
        }
    }
