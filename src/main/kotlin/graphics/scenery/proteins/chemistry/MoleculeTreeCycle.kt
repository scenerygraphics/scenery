package graphics.scenery.proteins.chemistry

class MoleculeTreeCycle(element: String, var cyclesAndChildren: List<List<MoleculeTree>>, bondOrder: Int, id: String = ""):
    MoleculeTree(element = element, bondOrder, id) {
        init {
            cyclesAndChildren.filter { it.size == 1 }.forEach {
                this.boundMolecules.add(it[0])
            }
        }

        fun renameAminoIdCycle(number: Int) {
            val mutable = mutableListOf<MutableList<MoleculeTree>>()
            this.cyclesAndChildren.forEach {
                mutable.add(it as MutableList)
            }
            mutable.forEach { child ->
                child.forEach {
                    it.renameAminoAcidIds(number)
                }
            }
            this.cyclesAndChildren = mutable
        }
    }
