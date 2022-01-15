package graphics.scenery.proteins.chemistry

import java.lang.module.Configuration

class BondTreeCycle(element: String, val cyclesAndChildren: List<List<BondTree>>, bondOrder: Int):
    BondTree(element = element, bondOrder) {
        init {
            cyclesAndChildren.filter { it.size == 1 }.forEach {
                this.boundMolecules.add(it[0])
            }
        }
    }
