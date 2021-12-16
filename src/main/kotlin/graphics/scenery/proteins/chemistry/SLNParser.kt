package graphics.scenery.proteins.chemistry

import graphics.scenery.utils.LazyLogger

/**
 * Parses the sln format of chemical molecules.
 */
class SLNParser {
    val logger by LazyLogger()

    /**
     * The bondTree must have parents of every node for displaying the 3D structure. However, this makes parsing more
     * complicated. This class serves as an intermediate step to compute the final tree structure
     */
    class HelperTree(val element: String, val bondOrder: Int = 0, val children: List<HelperTree>) {
    }


    /**
     * Parses the sln string and returns a BondTree
     */
    fun helperTree(sln: String): HelperTree {
        //CH3CH2C(=O)OH
        //build the HelperTree without information about the children
        var workingSLN = prepareSLNString(sln)
        //end of recursion
        if(workingSLN.length == 1) {
            return (HelperTree(workingSLN, 1, ArrayList()))
        }
        else {
            var bondOrder = 1
            val children = ArrayList<HelperTree>(4)
            val root = when {
                (workingSLN.startsWith("="))-> {
                    bondOrder = 2
                    workingSLN.take(2)
                }
                (workingSLN.startsWith("#")) -> {
                    bondOrder = 3
                    workingSLN.take(2)
                }
                else -> { workingSLN.take(1) }
            }
            var rest = workingSLN.drop(root.length)
            if(rest.startsWith("(")) {
                var openBracketsCount = 1
                var closedBracketsCount = 0
                var i = 1
                while(openBracketsCount != closedBracketsCount) {
                    if (rest[i] == '(') { openBracketsCount += 1 }
                    else if (rest[i] == ')') { closedBracketsCount += 1}
                    i += 1
                }
                children.add(helperTree(rest.drop(1).take(i-2)))
                children.add(helperTree(rest.drop(i)))
                return(HelperTree(root, 1, children))
            }
            while (rest.startsWith("H")){
                children.add(HelperTree("H", 1, listOf()))
                rest = rest.drop(1)
            }
            //end of the recursion
            if(rest.isEmpty()) {
                return HelperTree(root, bondOrder, children)
            }
            children.add(helperTree(rest))
            return(HelperTree(root, 1, children))
        }
    }

    /**
     * prepares the string so that all brackets are there, e.g., CH3CH2C(=O)OH becomes
     * C (H)(H)(H)(C (H)(H)(C (=O)(O (H))))
     */
    fun prepareSLNString(sln: String): String {
        var mutableSLN = sln
        if (mutableSLN.isEmpty()) {
            logger.warn("your sln is empty")
        }
        mutableSLN = sln.filterNot { it == '-' }
        for (i in 1..5) {
            var hydrogens = ""
            for (j in 0 until i) {
                hydrogens = hydrogens.plus("H")
            }
            mutableSLN = mutableSLN.replace("H$i", hydrogens)
        }
        return mutableSLN
    }



}

