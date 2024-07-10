package graphics.scenery.proteins

import graphics.scenery.geometry.DummySpline
import graphics.scenery.geometry.Spline
import graphics.scenery.geometry.UniformBSpline
import org.joml.*
import graphics.scenery.numerics.Random
import graphics.scenery.Mesh
import graphics.scenery.geometry.curve.DefaultCurve
import graphics.scenery.geometry.curve.Helix
import graphics.scenery.geometry.curve.Shape
import graphics.scenery.geometry.curve.Vertex
import org.biojava.nbio.structure.Atom
import org.biojava.nbio.structure.Group
import org.biojava.nbio.structure.secstruc.SecStrucCalc
import org.biojava.nbio.structure.secstruc.SecStrucElement
import org.biojava.nbio.structure.secstruc.SecStrucTools
import org.biojava.nbio.structure.secstruc.SecStrucType
import kotlin.math.min

/**
 * A Ribbon Diagram for Proteins. Ribbon models are currently the most common representations of proteins.
 * They draw a Spline approximately along the Backbone and highlight secondary structures like alpha helices
 * with a wider curve along the helix, beta sheets are represented with arrows pointing from the N-terminal
 * to the C-terminal. If you want to know more about the Ribbon diagrams, see the Wikipedia article:
 * https://en.wikipedia.org/wiki/Ribbon_diagram
 * In case you want to dig deeper here is an article of Jane D. Richardson, who developed the Ribbon:
 * https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3535681/ (Studying and Polishing the PDB's Macromolecules)
 *
 * In the code below, we use a Uniform B-Spline to draw the C-alpha trace of the protein. The controlpoints
 * are created via the algorithm of Carlson and Bugg (Algorithm for ribbon models of proteins, Carson et. al)
 * The algorithm roughly works like this:
 * Take a list of coordinates of C-alpha-atoms (Ca) and the coordinates
 * of the Oxygen-atoms (O) of their carbonyl groups. Form the vectors:
 *
 * A(i) = Ca(i)-Ca(i+1)  (i is the index)
 * B(i) = O(i) - Ca(i)
 * C = A X B  (X is the cross product)
 * D = A X C
 *
 * After normalizing C and D, we now form the midpoints between two consecutive C-alpha atoms:
 * P = [Ca(i)-Ca(i+1)]/2
 *
 * Then we scale D by the desired Ribbon width. Finally, we calculate the guide points:
 * P1 = P - D
 * P2 = P + D
 *
 * For the implementation we have consulted KiNG, a visualization software by David C. Richardson et al:
 * https://www.ncbi.nlm.nih.gov/pmc/articles/PMC2788294/
 * (KING (Kinemage, Next Generation): A versatile interactive molecular and scientific visualization program,
 * by Richardson et al.)
 *
 * @author Justin Buerger <burger@mpi-cbg.de>
 * @param [protein]                 The polypeptide you wish to visualize, stored in the class Protein
 *
 * @param [showSecondaryStructures] Whether or not to show secondary structures as well
 *
 * @param [verticesPerSection]      How many vertices should be rendered per section. The default is 20, and can also
 *                                  be overridden by the system property `scenery.Proteins.VerticesPerSection`.
 */

class RibbonDiagram(
    val protein: Protein,
    private val showSecondaryStructures: Boolean = false,
    val verticesPerSection: Int = System.getProperty("scenery.Proteins.VerticesPerSection", "20").toInt()
): Mesh("ribbon") {

    /*
     *[structure] the structure of the protein stored in the class Structure of BioJava
     *[chains] all the chains of the protein (C-alpha trace and sidechains)
     *[groups] atomGroups within the chains
     *[widthAlpha] specifies how wide outside the C-alpha trace the controlpoints
     * of the alpha helices will be
     *[widthBeta] specifies how wide outside the C-alpha trace the controlpoints
     * of the beta sheets will be
     *[widthCoil] specifies how wide outside the C-alpha trace the controlpoints
     * of the coil will be. The value 1 corresponds to the point laying approximately on
     * the trace.
     *[aminoList] List of all the amino acids in the protein
     *[sectionVerticesCount] Specifies how fine grained the geometry along the backbone
     * will be. Please note that the calculation could take much longer if this parameter is too
     * big, especially for large proteins.
     * [chainList} filtered list of the chains to be used
     */
    private val structure = protein.structure
    private val chains = structure.chains
    private val groups = chains.flatMap { it.atomGroups }
    private val widthAlpha = 2.0f
    private val widthBeta = 2.2f
    private val widthCoil = 1.0f
    private val chainList =  ArrayList<List<Group>>(groups.size)
    private val secStruc = dssp()
    //calculate the centroid of the protein
    private val centroid = Axis.getCentroid(groups.flatMap { it.atoms }.filter{it.name == "CA"}.map{it.getVector()})

    /**
     * Returns the final Ribbon Diagram
     */
    init {
        chains.forEach{ chain ->
            if(chain.isProtein) {
                val aminoList = ArrayList<Group>(chain.atomGroups.size)
                chain.atomGroups.forEach { group ->
                    if (group.hasAminoAtoms()) {
                        aminoList.add(group)
                    }
                }
                chainList.add(aminoList)
            }
        }

        chainList.forEach { aminoList ->
            val guidePointList = calculateGuidePoints(aminoList, secStruc)
            val spline = ribbonSpline(guidePointList)
            val subProtein = subShapes(guidePointList, spline)
            if(!showSecondaryStructures) {
                val rainbow = Rainbow()
                rainbow.colorVector(subProtein)
            }
            this.addChild(subProtein)

        }
    }

    /**
     * The baseShape for the Spline, the coil is represented with an octagon,
     * the helices with rectangles, and the sheets with arrows.
     */
    private fun subShapes(guidePointList: ArrayList<GuidePoint>, spline: Spline): Mesh {

        val subParent = Mesh("SubProtein")

        val splinePointCentered = spline.splinePoints().map{ it.sub(centroid) }
        val splinePoints = if(splinePointCentered.isNotEmpty())
        //the very first point of the spline is neglected as this makes the size of the point list divisible by the
        //section vertices count
        { splinePointCentered.subList(0, splinePointCentered.lastIndex) }
        else{splinePointCentered}

        /*
        In the following lines of code(144-211), we build a curve for each secondary structure. How does this work, step
        by step? First, we iterate through the guide points. A guide point represents a residue, therefore, it can be
        assigned a secondary structure or none, respectively. Each secondary structure is of a certain length and has a
        certain type (or none). With the type we adapt the curve base shapes: rectangles for the helices, small octagons
        for loops, and flat rectangles for the sheets. What is missing are the spline points. Fortunately, we already
        have a spline for the whole backbone. We take the points belonging to this section and put them into a new
        spline ("subSpline"). Now, an instance of the curve class is created, with the base shapes and the subSpline as
        properties. When displaySS is true this curve gets its parent, depending of course on its secondary structure
        type. In the end, it would satisfy this tree structure:
                                                PDB (protein)
                                               | | ...  |
                                              /  | ...   \
                                             /   | ...    \
                                            /    | ...     \
                                           /     | ...      \
                                  subUnit 1      subUnit 2 ...subUnit n
                                 |   |   |        |   |   |    ....
                                /    |    \      /    |    \
                               /     |     \    /     |     \
                          alphas  coils betas  alphas coils betas
         */
        val alphas = Mesh("alpha")
        val betas = Mesh("beta")
        val coils = Mesh("coil")

        //exclude dummy points for spline calculation
        val ssSections = getSecondaryStructureLengths(guidePointList.drop(1).dropLast(1), splinePoints, verticesPerSection)
        //configure offset for helix computation below
        var guidePointOffset = 1
        //iterate through all secondary structure sections
        ssSections.forEach { section ->
            val type = section.type
            val length = section.length
            val subSpline = section.subSpline
            val guidePointIndex = guidePointOffset

            // Helix needs separate treatment as the shapes are not oriented along the tangent but the helix axis
            if(type == SecStrucType.helix4 && length >= 4) {
                val caList = ArrayList<Vector3f?>(length)
                for (k in 0 until length) {
                    caList.add(guidePointList[guidePointIndex + k].nextResidue?.getAtom("CA")?.getVector())
                }
                val axis = Axis(caList)
                val axisLine = PositionDirection(axis.position, axis.direction)
                val helixCurve = Helix(axisLine, DummySpline(subSpline, verticesPerSection)) { listOf(Shape.makeRectangle()) }
                if (showSecondaryStructures) {
                    alphas.addChild(helixCurve)
                } else {
                    subParent.addChild(helixCurve)
                }
            }
            else {
                val ssSubList = ArrayList<Shape>(verticesPerSection * length)

                val iterationLength = subSpline.size


                //the beta sheets are visualized with arrows
                if (type.isBetaStrand) {
                    val seventyPercent = (iterationLength * 0.70).toInt()
                    for (j in 0 until seventyPercent) {
                        ssSubList.add(Shape.makeReversedRectangle())
                    }
                    val thirtyPercent = iterationLength - seventyPercent
                    for (j in thirtyPercent downTo 1) {
                        val y = 1.65f * j / thirtyPercent
                        val x = 0.1f
                        ssSubList.add(
                            Shape(listOf(
                                Vertex(Vector3f(x, y, 0f), Vector3f(), Vector2f()),
                                Vertex(Vector3f(-x, y, 0f), Vector3f(), Vector2f()),
                                Vertex(Vector3f(-x, -y, 0f), Vector3f(), Vector2f()),
                                Vertex(Vector3f(x, -y, 0f), Vector3f(), Vector2f())
                            ))
                        )
                    }
                    val betaCurve = DefaultCurve(
                        DummySpline(subSpline, verticesPerSection), { ssSubList })
                    if (showSecondaryStructures) {
                        betas.addChild(betaCurve)
                    } else {
                        subParent.addChild(betaCurve)
                    }
                } else {
                    for (j in 0 until iterationLength) {
                        ssSubList.add(Shape.makeOctagon())
                    }
                    val coilCurve =
                        DefaultCurve(DummySpline(subSpline, verticesPerSection), { ssSubList })
                    if (showSecondaryStructures) {
                        coils.addChild(coilCurve)
                    } else {
                        subParent.addChild(coilCurve)
                    }
                }
            }
            if (showSecondaryStructures) {
                subParent.addChild(alphas)
                subParent.addChild(betas)
                subParent.addChild(coils)
            }
            guidePointOffset += length
        }
        return subParent
    }


    /**
     * Returns the secondary structures of a protein, calculated with the dssp algorithm. For additional
     * information about the algorithm see https://swift.cmbi.umcn.nl/gv/dssp/
     */
    private fun dssp(): List<SecStrucElement> {
        //see: https://github.com/biojava/biojava-tutorial/blob/master/structure/secstruc.md
        val ssc = SecStrucCalc()
        ssc.calculate(structure, true)
        return SecStrucTools.getSecStrucElements(structure)
    }

    /**
     * data class containing two B-Splines, which will be melted into one.
     */
    data class SplineSkeleton(
        val splineSkeleton1: ArrayList<Vector3f>,
        val splineSkeleton2: ArrayList<Vector3f>)
    /**
     * Calculates the splineSkeleton out of the GuidePoints
     */
    private fun splineSkeleton(guidePoints: ArrayList<GuidePoint>): SplineSkeleton {
        val pts1 = ArrayList<Vector3f>(guidePoints.size)
        val pts2 = ArrayList<Vector3f>(guidePoints.size)
        guidePoints.forEach{
            val ribWith = if(it.offset > 0f){widthAlpha} else {widthBeta}
            val halfwidth = 0.5f*(widthCoil + it.widthFactor*(ribWith-widthCoil))
            val dVecPlus = Vector3f()
            it.finalPoint.add(it.dVec.mul(halfwidth, dVecPlus), dVecPlus)
            val dVecMinus = Vector3f()
            it.finalPoint.add(it.dVec.mul(-halfwidth, dVecMinus), dVecMinus)
            pts1.add(dVecPlus)
            pts2.add(dVecMinus)
        }
        return SplineSkeleton(pts1, pts2)
    }

    /**
     * Melts the two B-Splines of the SplineSkeleton into one Dummy-Spline
     * (a spline which list of controlPoints is equal to its guidePoints)
     */
    private fun ribbonSpline(guidePoints: ArrayList<GuidePoint>): Spline {
        val finalSpline = ArrayList<Vector3f>(guidePoints.size * verticesPerSection)
        val skeleton = splineSkeleton(guidePoints)
        val spline1 = UniformBSpline(skeleton.splineSkeleton1, verticesPerSection).splinePoints()
        val spline2 = UniformBSpline(skeleton.splineSkeleton2, verticesPerSection).splinePoints()
        spline1.forEachIndexed { i, _ ->
            val splinePoint = Vector3f()
            spline1[i].add(spline2[i], splinePoint)
            splinePoint.mul(0.5f)
            finalSpline.add(splinePoint)
        }
        return DummySpline(finalSpline, verticesPerSection)
    }

    companion object {



        /**
         * Calculates the GuidePoints from the list of amino acids.
         */
        @JvmStatic
        fun calculateGuidePoints(aminoList: List<Group>, ssList: List<SecStrucElement>): ArrayList<GuidePoint> {
            //To include all points in the visualization, dummy points need to be made.
            //First, a list without these dummy points is calculated.
            val guidePointsWithoutDummy = ArrayList<GuidePoint>(aminoList.size - 1)
            val guidePoints = ArrayList<GuidePoint>(aminoList.size + 4 - 1)
            val maxOffSet = 1.5f
            aminoList.dropLast(1).forEachIndexed { i, _ ->
                val ca1 = aminoList[i].getAtom("CA")
                val ca2 = aminoList[i + 1].getAtom("CA")
                val ca1Vec = ca1.getVector()
                val ca2Vec = ca2.getVector()
                val aVec = Vector3f()
                ca1Vec.sub(ca2Vec, aVec)
                val o = aminoList[i].getAtom("O")
                val bVec = o.getVector()

                val finalPoint = Vector3f()
                ca1Vec.add(ca2Vec, finalPoint)
                finalPoint.mul(0.5f)
                //See Carlson et. al
                val cVec = Vector3f()
                aVec.cross(bVec, cVec)
                cVec.normalize()
                val dVec = Vector3f()
                cVec.cross(aVec, dVec)
                cVec.normalize()
                val widthFactor: Float
                val offset: Float
                /*
                 Ribbons widen in areas of high curvature, respectively helices, and
                 low curvature, respectively beta sheets. The factors are listed in
                 the table* below:

                 CA-CA DIST  WIDTH FACTOR    OFFSET FACTOR   NOTE
                 ==========  ============    =============   ====
                   5.0         1               1               ~limit for curled-up protein
                   5.5         1               1               } linear interpolation
                   7.0         0               0               } from 1.0 to 0.0
                   9.0         0               0               } linear interpolation
                   10.5        1               0               } from 1.0 to 0.0
                   11.0        1               0               ~limit for extended protein

                   *Copyright (C) 2002-2007 Ian W. Davis & Vincent B. Chen. All rights reserved
                 */
                when {
                    (i >= 1 && i < aminoList.size - 3) -> {
                        val ca0 = aminoList[i - 1].getAtom("CA").getVector()
                        val ca3 = aminoList[i + 2].getAtom("CA").getVector()
                        val ca0Ca3 = Vector3f()
                        ca0.sub(ca3, ca0Ca3)
                        val ca0Ca3Distance = ca0.distance(ca3)
                        when {
                            (ca0Ca3Distance < 7f) -> {
                                widthFactor = java.lang.Float.min(1.5f, 7f - ca0Ca3Distance) / 1.5f
                                offset = widthFactor
                                val ca0Ca3Midpoint = Vector3f()
                                ca0Ca3.mul(0.5f, ca0Ca3Midpoint)
                                val offsetVec = Vector3f()
                                finalPoint.sub(ca0Ca3Midpoint, offsetVec)
                                offsetVec.normalize().mul(offset * maxOffSet)
                                finalPoint.add(offsetVec)
                            }
                            (ca0Ca3Distance > 9f) -> {
                                widthFactor = min(1.5f, ca0Ca3Distance - 9f) / 1.5f
                                offset = 0f
                            }
                            else -> {
                                widthFactor = 0f
                                offset = 0f
                            }
                        }
                    }
                    else -> {
                        widthFactor = 0f
                        offset = 0f
                    }
                }
                guidePointsWithoutDummy.add(i, GuidePoint(finalPoint, cVec, dVec, offset, widthFactor,
                    aminoList[i], aminoList[i + 1], SecStrucType.bend, 0)
                )
            }

            guidePointsWithoutDummy.forEachIndexed { index, guide ->
                ssList.forEach { ss ->
                    if (ss.range.start == guide.nextResidue!!.residueNumber) {
                        for (i in 0 until ss.range.length) {
                            if(ss.type == SecStrucType.helix4 && ss.range.length < 4) {
                                guidePointsWithoutDummy[index + i].type = SecStrucType.bend
                            }
                            else {
                                guidePointsWithoutDummy[index + i].type = ss.type
                            }
                            guidePointsWithoutDummy[index + i].ssLength = ss.range.length - 1
                        }
                    }
                }
            }


            //only assign widthFactor if there are three guide points with one in a row
            guidePointsWithoutDummy.windowed(5, 1) { window ->
                when {
                    (window[0].widthFactor == 0f && window[4].widthFactor == 0f) -> {
                        if (window[1].widthFactor == 0f || window[2].widthFactor == 0f || window[3].widthFactor == 0f) {
                            window[1].widthFactor = 0f
                            window[2].widthFactor = 0f
                            window[3].widthFactor = 0f
                        }
                    }
                }


            }
            if(guidePointsWithoutDummy.isEmpty()) {
                return guidePointsWithoutDummy
            }
            //if there is a width factor is still assigned at the beginning, also assign it to the first point
            if (guidePointsWithoutDummy[1].widthFactor != 0f && guidePointsWithoutDummy[2].widthFactor != 0f &&
                guidePointsWithoutDummy[3].widthFactor != 0f) {
                guidePointsWithoutDummy[0].widthFactor = guidePointsWithoutDummy[1].widthFactor
            }
            //if there is a width factor is still assigned at the and, also assign it to the last point
            if (guidePointsWithoutDummy.dropLast(1).last().widthFactor != 0f &&
                guidePointsWithoutDummy.dropLast(2).last().widthFactor != 0f &&
                guidePointsWithoutDummy.dropLast(3).last().widthFactor != 0f) {
                guidePointsWithoutDummy.last().widthFactor = guidePointsWithoutDummy.dropLast(1).last().widthFactor
            }

            //dummy points at the beginning
            val caBegin = aminoList[0].getAtom("CA").getVector()
            //increase the count of the first section because we add one more point
            val count = guidePointsWithoutDummy[0].ssLength
            for (i in 0..count) {
                guidePointsWithoutDummy[i].ssLength++
            }
            val dummyVecBeg = caBegin.randomFromVector()
            guidePoints.add(
                GuidePoint(dummyVecBeg, guidePointsWithoutDummy[0].cVec, guidePointsWithoutDummy[0].dVec,
                    guidePointsWithoutDummy[0].offset, guidePointsWithoutDummy[0].widthFactor, aminoList[0], aminoList[0],
                    SecStrucType.bend, 0)
            )
            guidePoints.add(
                GuidePoint(caBegin, guidePointsWithoutDummy[0].cVec, guidePointsWithoutDummy[0].dVec,
                    guidePointsWithoutDummy[0].offset, guidePointsWithoutDummy[0].widthFactor, aminoList[0], aminoList[0],
                    SecStrucType.bend, 0)
            )
            //add all guide points from the previous calculation
            guidePoints.addAll(guidePointsWithoutDummy)
            //add dummy points at the end
            val caEnd = aminoList.last().getAtom("CA").getVector()
            guidePoints.add(
                GuidePoint(caEnd,
                    guidePointsWithoutDummy.last().cVec, guidePointsWithoutDummy.last().dVec,
                    guidePointsWithoutDummy.last().offset, guidePointsWithoutDummy.last().widthFactor,
                    aminoList.last(), aminoList.last(), SecStrucType.bend,
                    guidePointsWithoutDummy.last().ssLength)
            )
            val dummyVecEnd = caEnd.randomFromVector()
            guidePoints.add(
                GuidePoint(dummyVecEnd,
                    guidePointsWithoutDummy.last().cVec, guidePointsWithoutDummy.last().dVec,
                    guidePointsWithoutDummy.last().offset, guidePointsWithoutDummy.last().widthFactor,
                    aminoList.last(), aminoList.last(), SecStrucType.bend,
                    0)
            )

            return untwistRibbon(guidePoints)
        }

        /**
         * The calculation of the helices results in twists of the curve. This function
         * takes a list of GuidePoints and untwists them.
         */
        private fun untwistRibbon(guidePoints: ArrayList<GuidePoint>): ArrayList<GuidePoint> {
            guidePoints.forEachIndexed { i, _ ->
                if (i > 0) {
                    if (guidePoints[i].dVec.dot(guidePoints[i - 1].dVec) < 0f) {
                        guidePoints[i].dVec.mul(-1f)
                    }
                }
            }
            return guidePoints
        }


        /**
         * Extension function to make a Vector out of an atom position. We do not
         * need any information about an atom besides its name and its position.
         */
        @JvmStatic
        fun Atom.getVector(): Vector3f {
            return Vector3f(this.x.toFloat(), this.y.toFloat(), this.z.toFloat())
        }

        /**
         * Extension Function to make Dummy Points not too far away from the original points - the spline
         * doesn't include the first and the last controlpoint, which in our case would mean to lose the first
         * and the last residue, hence, this function.
         */
        private fun Vector3f.randomFromVector(): Vector3f {
            return Vector3f(Random.randomFromRange(this.x() - 0.1f, this.x() + 0.1f),
                Random.randomFromRange(this.y() - 0.1f, this.y() + 0.1f),
                Random.randomFromRange(this.z() - 0.1f, this.z() + 0.1f))
        }
    }

    /**
     * Stores a section of a secondary structure
     */
    data class SecondaryStructureSection(val type: SecStrucType, val length: Int, val subSpline: List<Vector3f>)

    /**
     * Counts the secondary structure length and puts together the subspline
     */
    private fun getSecondaryStructureLengths(guidePoints: List<GuidePoint>, splinePoints: List<Vector3f>, sectionCount: Int):
        List<SecondaryStructureSection> {
        // we do not know in advance how many secondary structures there will be in the chain
        val ssSections = ArrayList<SecondaryStructureSection>(guidePoints.size/5)
        var splineOffset = 0
        var guidePointOffset = 0
        while(guidePointOffset < guidePoints.lastIndex) {
            val guide = guidePoints[guidePointOffset]
            val type = guide.type
            val length = if(type.isBetaStrand || type == SecStrucType.helix4)
            { guide.ssLength+1 }
            else {
                //fuse together the coils
                val immutableOffset = guidePointOffset
                var thisLength = guide.ssLength+1
                var nextGuide = guidePoints[immutableOffset + thisLength]
                while(!nextGuide.type.isBetaStrand && nextGuide.type != SecStrucType.helix4 && immutableOffset + thisLength < guidePoints.lastIndex) {
                    thisLength += (nextGuide.ssLength)
                    thisLength ++
                    nextGuide = guidePoints[immutableOffset + thisLength]
                }
                thisLength
            }
            val subSplineLength = sectionCount*length
            val subSpline = ArrayList<Vector3f>(subSplineLength+1)
            val immutableSplineOffset = splineOffset
            for(i in 0 .. subSplineLength) {
                val index = immutableSplineOffset + i
                if(index <= splinePoints.lastIndex) {
                    subSpline.add(splinePoints[index])
                }

                else {
                    break
                }
            }
            guidePointOffset += length
            splineOffset += subSplineLength
            ssSections.add(SecondaryStructureSection(guide.type, length, subSpline))
        }
        return ssSections
    }
}
