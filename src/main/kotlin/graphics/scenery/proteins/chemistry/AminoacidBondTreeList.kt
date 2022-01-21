package graphics.scenery.proteins.chemistry

import java.util.concurrent.CopyOnWriteArrayList

/**
 *
 */
class AminoTreeList {
    val aminoMap = hashMapOf<String, MoleculeTree>()
    init {
        //Alanine
        val alanine = aminoAcid()
        val cbAlanine = MoleculeTree("C", 1)
        cbAlanine.addhydrogen(3)
        alanine.findByID("Ca")!!.addMolecule(cbAlanine)
        aminoMap["ALA"] = alanine

        //Arginine
        val arginine = aminoAcid()
        val cb = MoleculeTree("C", 1)
        cb.addhydrogen(1)
        val cg = MoleculeTree("C", 1)
        cg.addhydrogen(2)
        val cd = MoleculeTree("C", 1)
        cd.addhydrogen(2)
        val en = MoleculeTree("N", 1)
        en.addhydrogen(1)
        val cz = MoleculeTree("C", 1)
        val n1 = MoleculeTree("N", 2)
        n1.addhydrogen(1)
        val n2 = MoleculeTree("N", 1)
        n2.addhydrogen(2)
        cz.addMolecule(n1)
        cz.addMolecule(n2)
        en.addMolecule(cz)
        cd.addMolecule(en)
        cg.addMolecule(cd)
        cb.addMolecule(cd)
        cb.addhydrogen(1)
        arginine.findByID("Ca")!!.addMolecule(cb)
        aminoMap["ARG"] = arginine

        //Asparagine
        val asparagine = aminoAcid()
        val cbAsn = MoleculeTree("C", 1)
        cbAsn.addhydrogen(2)
        val cgAsn = MoleculeTree("C", 1)
        val o1Asn = MoleculeTree("O", 2)
        val n1Asn = MoleculeTree("N", 1)
        n1Asn.addhydrogen(2)
        cgAsn.addMolecule(n1Asn)
        cgAsn.addMolecule(o1Asn)
        cbAsn.addMolecule(cgAsn)
        asparagine.findByID("Ca")!!.addMolecule(cbAsn)
        aminoMap["ASN"] = asparagine

        //Cysteine
        val cysteine = aminoAcid()
        val cbCys = MoleculeTree("C", 1)
        cbCys.addhydrogen(2)
        val cgCys = MoleculeTree("C", 1)
        val sdCys = MoleculeTree("S", 1)
        sdCys.addhydrogen(1)
        cgCys.addMolecule(sdCys)
        cbCys.addMolecule(cgCys)
        cgCys.addhydrogen(2)
        cysteine.findByID("Ca")!!.addMolecule(cbCys)
        aminoMap["CYS"] = cysteine

        //Glutamine
        val glutamine = aminoAcid()
        val cbGln = MoleculeTree("C", 1)
        cbGln.addhydrogen(1)
        val cgGln = MoleculeTree("C", 1)
        val cdGln = MoleculeTree("C", 1)
        val neGln = MoleculeTree("N", 1)
        neGln.addhydrogen(2)
        val oeGln = MoleculeTree("O", 2)
        cdGln.addMolecule(neGln)
        cdGln.addMolecule(oeGln)
        cgGln.addMolecule(cdGln)
        cgGln.addhydrogen(2)
        cbGln.addhydrogen(1)
        cbGln.addMolecule(cgGln)
        glutamine.findByID("Ca")!!.addMolecule(cbGln)
        aminoMap["GLN"] = glutamine

        //Proline
        val caP = MoleculeTree("C", 1, "Ca")
        val cP = MoleculeTree("C", 1, "C")
        val oP = MoleculeTree("O", 2)
        val oP2 = MoleculeTree("O", 1, "OH")
        val hOP2 = MoleculeTree("H", 1, "HO")
        oP2.addMolecule(hOP2)
        cP.addMolecule(oP2)
        cP.addMolecule(oP)
        caP.addMolecule(cP)
        caP.addhydrogen(1)
        val c1P = MoleculeTree("C", 1)
        c1P.addhydrogen(2)
        val c2P = MoleculeTree("C", 1)
        c2P.addhydrogen(2)
        val c3P = MoleculeTree("C", 1)
        c3P.addhydrogen(2)
        val hn = MoleculeTree("H", 1, "HN")
        val proline = MoleculeTreeCycle("N", listOf(listOf(caP, c1P, c2P, c3P), listOf(hn)),1, "N")
        aminoMap["PRO"] = proline

        //Tryptophane
        val cbTryptophane = MoleculeTree("C", 1, "cb")
        cbTryptophane.addhydrogen(2)
        val cycle1c1 = MoleculeTree("C", 2)
        cycle1c1.addhydrogen(1)
        val cycleN = MoleculeTree("N", 1)
        cycleN.addhydrogen(1)
        val cycle2c1 = MoleculeTree("C", 1)
        cycle2c1.addhydrogen(1)
        val cycle2c2 = MoleculeTree("C", 2)
        cycle2c2.addhydrogen(1)
        val cycle2c3 = MoleculeTree("C",    1)
        cycle2c3.addhydrogen(1)
        val cycle2c4 = MoleculeTree("C",   2)
        cycle2c4.addhydrogen(1)
        val cycle1c3 = MoleculeTree("C", 2)
        cycle1c3.addhydrogen(1)
        val cycle1c2 = MoleculeTreeCycle("C", listOf(listOf(cycle2c1, cycle2c2, cycle2c3, cycle2c4, cycle1c3), listOf(
            MoleculeTree("H", 1)
        )), 1)
        val cycle1 = MoleculeTreeCycle("C", listOf(listOf(cycle1c1, cycleN,  cycle1c2, cycle1c3)), 1)
        cbTryptophane.addMolecule(cycle1)
        val tryptophane = aminoAcid()
        tryptophane.findByID("Ca")!!.addMolecule(cbTryptophane)
        aminoMap["TRP"] = tryptophane

        //Serine
        val cbSer = MoleculeTree("C", 1)
        val oSer = MoleculeTree("O", 1)
        oSer.addhydrogen(1)
        cbSer.addhydrogen(1)
        cbSer.addMolecule(oSer)
        val serine = aminoAcid()
        serine.findByID("Ca")!!.addMolecule(cbSer)
        aminoMap["SER"] = serine

        //Threonine
        val cbThr = MoleculeTree("C", 1)
        cbThr.addMolecule(hydroxyGroup())
        cbThr.addMolecule(ch3())
        val threonine = aminoAcid()
        threonine.findByID("Ca")!!.addMolecule(cbThr)
        aminoMap["THR"] = threonine

        //Glycine
        val hGly = MoleculeTree("H", 1)
        val glycine = aminoAcid()
        glycine.findByID("Ca")!!.addMolecule(hGly)
        aminoMap["GLY"] = glycine

        //Valine
        val cbVal = MoleculeTree("C", 1)
        cbVal.addMolecule(ch3())
        cbVal.addMolecule(ch3())
        cbVal.addhydrogen(1)
        val valine = aminoAcid()
        valine.findByID("Ca")!!.addMolecule(cbVal)
        aminoMap["VAL"] = valine

        //Aspartic Acid
        val cbAsp = MoleculeTree("C", 1)
        cbAsp.addhydrogen(2)
        val cgAsp = MoleculeTree("C",1)
        cgAsp.addMolecule(hydroxyGroup())
        val o1Asp = MoleculeTree("O", 2)
        cgAsp.addMolecule(o1Asp)
        cbAsp.addMolecule(cgAsp)
        val AsparticAcid = aminoAcid()
        AsparticAcid.findByID("Ca")!!.addMolecule(cbAsp)
        aminoMap["ASP"] = AsparticAcid

        //Glutamic Acid
        val cbGlu = MoleculeTree("C",1)
        cbGlu.addhydrogen(2)
        val cgGlu = MoleculeTree("C", 1)
        cgGlu.addhydrogen(1)
        cgGlu.addMolecule(carboxy())
        cgGlu.addhydrogen(1)
        cbGlu.addMolecule(cgGlu)
        val GlutamicAcid = aminoAcid()
        GlutamicAcid.findByID("Ca")!!.addMolecule(cbGlu)
        aminoMap["GLU"] = GlutamicAcid

        //Lysine
        val cbLys = MoleculeTree("C",1)
        cbLys.addhydrogen(1)
        val cgLys = MoleculeTree("C", 1)
        cgLys.addhydrogen(2)
        val cdLys = MoleculeTree("C", 1)
        cdLys.addhydrogen(2)
        val ceLys = MoleculeTree("C", 1)
        ceLys.addhydrogen(2)
        ceLys.addMolecule(aminoGroup())
        cgLys.addMolecule(ceLys)
        cbLys.addMolecule(cgLys)
        cbLys.addhydrogen(1)
        val Lysine = aminoAcid()
        Lysine.findByID("Ca")!!.addMolecule(cbLys)
        aminoMap["LYS"] = Lysine

        //Leucine
        val cbLeu = MoleculeTree("C", 1)
        cbLeu.addhydrogen(1)
        val cgLeu = MoleculeTree("C", 1)
        cgLeu.addMolecule(ch3())
        cgLeu.addMolecule(ch3())
        cgLeu.addhydrogen(1)
        cbLeu.addMolecule(cgLeu)
        cbLeu.addhydrogen(1)
        val Leucine = aminoAcid()
        Leucine.findByID("Ca")!!.addMolecule(cbLeu)
        aminoMap["LEU"] = Leucine

        //Isoleucine
        val cbIle = MoleculeTree("C", 1)
        cbIle.addMolecule(ch3())
        cbIle.addhydrogen(1)
        val cgIle = MoleculeTree("C", 1)
        cgIle.addhydrogen(1)
        cgIle.addMolecule(ch3())
        cgIle.addhydrogen(1)
        cbIle.addMolecule(cgIle)
        val Isoleucine = aminoAcid()
        Isoleucine.findByID("Ca")!!.addMolecule(cbIle)
        aminoMap["ILE"] = Isoleucine

        //Methionine
        val cbMet = MoleculeTree("C",1)
        cbMet.addhydrogen(1)
        val cgMet = MoleculeTree("C", 1)
        cgMet.addhydrogen(2)
        val s1Met = MoleculeTree("S", 1)
        s1Met.addMolecule(ch3())
        s1Met.addhydrogen(2)
        cgMet.addMolecule(s1Met)
        cbMet.addMolecule(cgMet)
        cbMet.addhydrogen(1)
        val Methionine = aminoAcid()
        Methionine.findByID("Ca")!!.addMolecule(cbMet)
        aminoMap["MET"] = Methionine

        //histidine
        val cbHis = MoleculeTree("C", 1)
        cbHis.addhydrogen(1)
        val hisN = MoleculeTree("N", 1)
        val c1His = MoleculeTree("C", 2)
        c1His.addhydrogen(1)
        val n2His = MoleculeTree("N", 1)
        n2His.addhydrogen(1)
        val c2His = MoleculeTree("C", 1)
        c2His.addhydrogen(1)
        val cycHis = MoleculeTreeCycle("C", listOf(listOf(hisN, c1His, n2His, c2His)), 2)
        cbHis.addMolecule(cycHis)
        cbHis.addhydrogen(1)
        val histidine = aminoAcid()
        histidine.findByID("Ca")!!.addMolecule(cbHis)
        aminoMap["HIS"] = histidine
        aminoMap["HID"] = histidine

        //phenylalanine
        val cbPhe = MoleculeTree("C", 1)
        cbPhe.addhydrogen(1)
        cbPhe.addMolecule(benzene())
        cbPhe.addhydrogen(1)
        val phenylalanine = aminoAcid()
        phenylalanine.findByID("Ca")!!.addMolecule(cbPhe)
        aminoMap["PHE"] = phenylalanine

        //tyrosine
        val cbTyr = MoleculeTree("C", 1)
        cbTyr.addhydrogen(1)
        cbTyr.addMolecule(benzene(true))
        cbTyr.addhydrogen(1)
        val tyrosine = aminoAcid()
        tyrosine.findByID("Ca")!!.addMolecule(cbTyr)
        aminoMap["TYR"] = tyrosine


    }

    /**
     * Proteinogenic amino acids have a similar structure, they only differ in their sidechains.
     * The only exception is proline.
     */
    private fun aminoAcid(): MoleculeTree {
        val n = MoleculeTree("N", 1, "N")
        n.addMolecule(MoleculeTree("H", 1, "HN"))
        n.addMolecule(MoleculeTree("H", 1, "HNB"))
        val ca = MoleculeTree("C", 1, "Ca")
        ca.addhydrogen(1)
        val c = MoleculeTree("C", 1, "C")
        val o1c = MoleculeTree("O", 2)
        val o2c = MoleculeTree("O", 1, "OH")
        o2c.addMolecule(MoleculeTree("H", 1, "HO"))
        c.addMolecule(o1c)
        c.addMolecule(o2c)
        ca.addMolecule(c)
        n.addMolecule(ca)
        return n
    }

    /**
     * returns a hydroxy group
     */
    private fun hydroxyGroup(): MoleculeTree {
        val o = MoleculeTree("O", 1)
        o.addhydrogen(1)
        return o
    }
    /**
     * returns amino group
     */
    private fun aminoGroup(): MoleculeTree {
        val n = MoleculeTree("N", 1)
        n.addhydrogen(2)
        return n
    }
    /**
     * return methane
     */
    private fun ch3(): MoleculeTree {
        val m = MoleculeTree("C", 1)
        m.addhydrogen(3)
        return m
    }
    /**
     * returns a carboxy group
     */
    private fun carboxy(): MoleculeTree {
        val c = MoleculeTree("C", 1)
        val o = MoleculeTree("O", 2)
        val oh = hydroxyGroup()
        c.addMolecule(o)
        c.addMolecule(oh)
        return c
    }

    /**
     * returns benzol
     */
    private fun benzene(hydroxAtC3: Boolean = false): MoleculeTreeCycle {
        val c1 = MoleculeTree("C", 1, "b1")
        c1.addhydrogen(1)
        val c2 = MoleculeTree("C", 2, "b2")
        c2.addhydrogen(1)
        val c3 = MoleculeTree("C", 1, "b3")
        if(hydroxAtC3) {
            c3.addMolecule(hydroxyGroup())
        }
        else {
            c3.addhydrogen(1)
        }
        val c4 = MoleculeTree("C", 2, "b4")
        c4.addhydrogen(1)
        val c5 = MoleculeTree("C", 1, "b5")
        c5.addhydrogen(1)
        return MoleculeTreeCycle("C", listOf(listOf(c1, c2, c3, c4, c5)), 2)
    }
}
