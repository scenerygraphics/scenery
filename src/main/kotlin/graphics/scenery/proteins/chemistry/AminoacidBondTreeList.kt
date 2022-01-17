package graphics.scenery.proteins.chemistry

/**
 *
 */
class AminoAcidBondTreeMap {
    val aminoMap = hashMapOf<String, BondTree>()
    init {
        //alanine
        val alanine = aminoAcidBluePrint()
        val cbAlanine = BondTree("C", 1)
        cbAlanine.addhydrogen(3)
        alanine.addBoundMolecule(cbAlanine)
        aminoMap["ALA"] = alanine

        //arginine
        val arginine = aminoAcidBluePrint()
        val cb = BondTree("C", 1)
        cb.addhydrogen(1)
        val cg = BondTree("C", 1)
        cg.addhydrogen(2)
        val cd = BondTree("C", 1)
        cd.addhydrogen(2)
        val en = BondTree("N", 1)
        en.addhydrogen(1)
        val cz = BondTree("C", 1)
        val n1 = BondTree("N", 2)
        n1.addhydrogen(1)
        val n2 = BondTree("N", 1)
        n2.addhydrogen(2)
        cz.addBoundMolecule(n1)
        cz.addBoundMolecule(n2)
        en.addBoundMolecule(cz)
        cd.addBoundMolecule(en)
        cg.addBoundMolecule(cd)
        cb.addBoundMolecule(cd)
        cb.addhydrogen(1)
        arginine.findByID("Ca")!!.addBoundMolecule(cb)
        aminoMap["ARG"] = arginine

        //asparagine
        val asparagine = aminoAcidBluePrint()
        val cbAsp = BondTree("C", 1)
        cbAsp.addhydrogen(2)
        val cgAsp = BondTree("C", 1)
        val o1Asp = BondTree("O", 2)
        val n1Asp = BondTree("N", 1)
        n1Asp.addhydrogen(2)
        cgAsp.addBoundMolecule(n1Asp)
        cgAsp.addBoundMolecule(o1Asp)
        cbAsp.addBoundMolecule(cgAsp)
        asparagine.findByID("Ca")!!.addBoundMolecule(cbAsp)
        aminoMap["ASP"] = asparagine
        aminoMap["ASN"] = asparagine

        //cysteine
        val cysteine = aminoAcidBluePrint()
        val cbCys = BondTree("C", 1)
        cbCys.addhydrogen(2)
        val cgCys = BondTree("C", 1)
        cgCys.addhydrogen(2)
        val sdCys = BondTree("S", 1)
        sdCys.addhydrogen(1)
        cgCys.addBoundMolecule(sdCys)
        cbCys.addBoundMolecule(cgCys)
        cysteine.findByID("Ca")!!.addBoundMolecule(cbCys)
        aminoMap["CYS"] = cysteine

        //glutamine
        val glutamine = aminoAcidBluePrint()
        val cbGln = BondTree("C", 1)
        cbGln.addhydrogen(1)
        val cgGln = BondTree("C", 1)
        val cdGln = BondTree("C", 1)
        val neGln = BondTree("N", 1)
        neGln.addhydrogen(2)
        val oeGln = BondTree("O", 2)
        cdGln.addBoundMolecule(neGln)
        cdGln.addBoundMolecule(oeGln)
        cgGln.addBoundMolecule(cdGln)
        cgGln.addhydrogen(2)
        cbGln.addhydrogen(1)
        cbGln.addBoundMolecule(cgGln)

        glutamine.findByID("Ca")!!.addBoundMolecule(cbGln)
        aminoMap["GLN"] = glutamine

        //proline
        val proline = BondTree("C", 0)
        val oP = BondTree("O", 2)
        val o2P = BondTree("O", 1)
        o2P.addhydrogen(1)
        //cycle elements
        val cP1 = BondTree("C", 1)
        cP1.addhydrogen(1)
        val cP2 = BondTree("C", 1)
        cP2.addhydrogen(1)
        val cP3 = BondTree("C", 1)
        cP3.addhydrogen(1)
        val nP = BondTree("N", 1, "N")
        nP.addhydrogen(1)
        val hcP = BondTree("H", 1)
        val cP = BondTreeCycle("C", listOf(listOf(cP1, cP2, cP3, nP), listOf(hcP)),1, "Ca")
        proline.addBoundMolecule(oP)
        proline.addBoundMolecule(o2P)
        proline.addBoundMolecule(cP)
        aminoMap["PRO"] = proline

        //Tryptophane
        val cbTryptophane = BondTree("C", 1, "cb")
        cbTryptophane.addhydrogen(2)
        val cycle1c1 = BondTree("C", 2)
        cycle1c1.addhydrogen(1)
        val cycleN = BondTree("N", 1)
        cycleN.addhydrogen(1)
        val cycle2c1 = BondTree("C", 1)
        cycle2c1.addhydrogen(1)
        val cycle2c2 = BondTree("C", 2)
        cycle2c2.addhydrogen(1)
        val cycle2c3 = BondTree("C",    1)
        cycle2c3.addhydrogen(1)
        val cycle2c4 = BondTree("C",   2)
        cycle2c4.addhydrogen(1)
        val cycle1c3 = BondTree("C", 2)
        cycle1c3.addhydrogen(1)
        val cycle1c2 = BondTreeCycle("C", listOf(listOf(cycle2c1, cycle2c2, cycle2c3, cycle2c4, cycle1c3), listOf(
            BondTree("H", 1)
        )), 1)
        val cycle1 = BondTreeCycle("C", listOf(listOf(cycle1c1, cycleN,  cycle1c2, cycle1c3)), 1)
        cbTryptophane.addBoundMolecule(cycle1)
        val tryptophane = aminoAcidBluePrint()
        tryptophane.findByID("Ca")!!.addBoundMolecule(cbTryptophane)
        aminoMap["TRP"] = tryptophane
    }

    /**
     * Proteinogenic amino acids have a similar structure, they only differ in their sidechains.
     * The only exception is proline.
     */
    private fun aminoAcidBluePrint(): BondTree {
        //c alpha atom functions as the parent
        val ca = BondTree("C", 1, "Ca")
        val n = BondTree("N", 1, "N")
        n.addBoundMolecule(BondTree("H", 1, "HN"))
        n.addBoundMolecule(BondTree("H", 1, "HNB"))
        ca.addBoundMolecule(n)
        ca.addhydrogen(1)
        val c = BondTree("C", 1, "C")
        val o1c = BondTree("O", 2)
        val o2c = BondTree("O", 1, "OH")
        o2c.addBoundMolecule(BondTree("H", 1, "HO"))
        c.addBoundMolecule(o1c)
        c.addBoundMolecule(o2c)
        c.addBoundMolecule(ca)
        return c
    }
}
