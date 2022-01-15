package graphics.scenery.proteins.chemistry

import org.biojava.nbio.structure.Bond

/**
 *
 */
class AminoAcidBondTreeMap {
    val aminoMap = hashMapOf<String, BondTree>()
    init {
        //alanine
        val alanine = aminoAcidBluePrint()
        val cbAlanine = BondTree("C", 1, alanine)
        cbAlanine.addhydrogen(3)
        alanine.addBoundMolecule(cbAlanine)
        aminoMap["ALA"] = alanine

        //arginine
        val arginine = aminoAcidBluePrint()
        val cb = BondTree("C", 1, arginine)
        cb.addhydrogen(1)
        val cg = BondTree("C", 1, cb)
        cg.addhydrogen(2)
        val cd = BondTree("C", 1, cg)
        cd.addhydrogen(2)
        val en = BondTree("N", 1, cd)
        en.addhydrogen(1)
        val cz = BondTree("C", 1, en)
        val n1 = BondTree("N", 2, cz)
        n1.addhydrogen(1)
        val n2 = BondTree("N", 1, cz)
        n2.addhydrogen(2)
        cz.addBoundMolecule(n1)
        cz.addBoundMolecule(n2)
        en.addBoundMolecule(cz)
        cd.addBoundMolecule(en)
        cg.addBoundMolecule(cd)
        cb.addBoundMolecule(cd)
        cb.addhydrogen(1)
        arginine.addBoundMolecule(cb)
        aminoMap["ARG"] = arginine

        //asparagine
        val asparagine = aminoAcidBluePrint()
        val cbAsp = BondTree("C", 1, asparagine)
        cbAsp.addhydrogen(2)
        val cgAsp = BondTree("C", 1, cbAsp)
        val o1Asp = BondTree("O", 2, cgAsp)
        val n1Asp = BondTree("N", 1, cgAsp)
        n1Asp.addhydrogen(1)
        cgAsp.addBoundMolecule(o1Asp)
        cgAsp.addBoundMolecule(n1Asp)
        cbAsp.addBoundMolecule(cgAsp)
        asparagine.addBoundMolecule(cbAsp)
        aminoMap["ASP"] = asparagine
        aminoMap["ASN"] = asparagine

        //cysteine
        val cysteine = aminoAcidBluePrint()
        val cbCys = BondTree("C", 1, cysteine)
        cbCys.addhydrogen(2)
        val cgCys = BondTree("C", 1, cbCys)
        cgCys.addhydrogen(2)
        val sdCys = BondTree("S", 1, cgCys)
        sdCys.addhydrogen(1)
        cgCys.addBoundMolecule(sdCys)
        cbCys.addBoundMolecule(cgCys)
        cysteine.addBoundMolecule(cbCys)
        aminoMap["CYS"] = cysteine

        //glutamine
        val glutamine = aminoAcidBluePrint()
        val cbGln = BondTree("C", 1, glutamine)
        cbGln.addhydrogen(1)
        val cgGln = BondTree("C", 1, cbGln)
        cgGln.addhydrogen(2)
        val cdGln = BondTree("C", 1, cgGln)
        val neGln = BondTree("N", 1, cdGln)
        neGln.addhydrogen(2)
        val oeGln = BondTree("O", 2, cdGln)
        cdGln.addBoundMolecule(neGln)
        cdGln.addBoundMolecule(oeGln)
        cgGln.addBoundMolecule(cdGln)
        cbGln.addBoundMolecule(cgGln)
        cbGln.addhydrogen(1)
        glutamine.addBoundMolecule(cbGln)
        aminoMap["GLN"] = glutamine

        //proline
        val proline = BondTree("C", 0, null)
        val oP = BondTree("O", 2, proline)
        val o2P = BondTree("O", 1, proline)
        o2P.addhydrogen(1)
        //cycle elements
        val cP1 = BondTree("C", 1, null)
        cP1.addhydrogen(1)
        val cP2 = BondTree("C", 1, null)
        cP2.addhydrogen(1)
        val cP3 = BondTree("C", 1, null)
        cP3.addhydrogen(1)
        val nP = BondTree("N", 1, null)
        nP.addhydrogen(1)
        val hcP = BondTree("H", 1, null)
        val cP = BondTreeCycle("C", listOf(listOf(cP1, cP2, cP3, nP), listOf(hcP)),1, proline)
        proline.addBoundMolecule(oP)
        proline.addBoundMolecule(o2P)
        proline.addBoundMolecule(cP)
        aminoMap["PRO"] = proline
    }

    /**
     * Proteinogenic amino acids have a similar structure, they only differ in their sidechains.
     * The only exception is proline.
     */
    fun aminoAcidBluePrint(): BondTree {
        //c alpha atom functions as the parent
        val ca = BondTree("C", 0, null)
        val n = BondTree("N", 1, ca)
        n.addhydrogen(2)
        val c = BondTree("C", 1, ca)
        val o1c = BondTree("O", 2, c)
        val o2c = BondTree("O", 1, c)
        o2c.addhydrogen(1)
        c.addBoundMolecule(o1c)
        c.addBoundMolecule(o2c)
        ca.addBoundMolecule(n)
        ca.addhydrogen(1)
        ca.addBoundMolecule(c)
        return ca
    }
}