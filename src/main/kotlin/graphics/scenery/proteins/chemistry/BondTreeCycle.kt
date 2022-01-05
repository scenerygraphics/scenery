package graphics.scenery.proteins.chemistry

import java.lang.module.Configuration

class BondTreeCycle(element: String, val cyclesAndChildren: List<List<BondTree>>, bondOrder: Int,  parent: BondTree?):
    BondTree(element = element, bondOrder, parent)
