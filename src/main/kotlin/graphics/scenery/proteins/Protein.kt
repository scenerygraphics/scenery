package graphics.scenery.proteins

import graphics.scenery.Mesh
import graphics.scenery.proteins.Protein.MyProtein.fromFile
import graphics.scenery.proteins.Protein.MyProtein.fromID
import graphics.scenery.utils.lazyLogger
import org.biojava.nbio.structure.Structure
import org.biojava.nbio.structure.StructureException
import org.biojava.nbio.structure.StructureIO
import org.biojava.nbio.structure.io.PDBFileReader
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.InvalidPathException

/**
 * Constructs a protein from a pdb-file.
 * @author  Justin Buerger <burger@mpi-cbg.de>
 * [fromID] loads a pbd-file with an ID. See also: https://www.rcsb.org/pages/help/advancedsearch/pdbIDs
 * [fromFile] loads a pdb-file from memory.
 */

class Protein(val structure: Structure): Mesh("Protein") {
    var id: String? = null

    companion object MyProtein {
        private val proteinLogger by lazyLogger()

        fun fromID(id: String): Protein {
            try {
                StructureIO.getStructure(id)
            } catch (ioe: IOException) {
                proteinLogger.error("Something went wrong during the loading process of the pdb file, " +
                        "maybe a typo in the pdb entry or you chose a deprecated one?" +
                    "Here is the trace: \n" +
                ioe.printStackTrace())
                throw ioe
            } catch(se: StructureException) {
                proteinLogger.error("Something went wrong during the loading of the pdb file."+
                "Here is the trace: \n" +
                se.printStackTrace())
                throw se
            } catch(npe: NullPointerException) {
                proteinLogger.error("Something is broken in BioJava. You can try to update the version.")
                throw npe
            }

            val structure = StructureIO.getStructure(id)
            val protein = Protein(structure)
            protein.id = id
            return protein
        }

        fun fromFile(path: String): Protein {
            val reader = PDBFileReader()
            //print("Please enter the path of your PDB-File: ")
            //val readPath = readLine()
            try {
                reader.getStructure(path)
            } catch (ipe: InvalidPathException) {
                proteinLogger.info("Path was invalid, maybe this helps: ${ipe.reason} " +
                    "or the index: ${ipe.index}")
                throw ipe
            } catch(fnfe: FileNotFoundException) {
                proteinLogger.error("The pdb file is not in the directory")
                throw fnfe
            }

            val structure = reader.getStructure(path)
            return Protein(structure)
        }

    }
}
