package graphics.scenery.proteins

import graphics.scenery.primitives.Mesh
import org.biojava.nbio.structure.*
import org.biojava.nbio.structure.io.PDBFileReader
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.InvalidPathException
import graphics.scenery.utils.LazyLogger
/**
 * Constructs a protein from a pdb-file.
 * @author  Justin Buerger <burger@mpi-cbg.de>
 * [fromID] loads a pbd-file with an ID. See also: https://www.rcsb.org/pages/help/advancedsearch/pdbIDs
 * [fromFile] loads a pdb-file from memory.
 */

class Protein(val structure: Structure): Mesh("Protein") {

    companion object MyProtein {
        private val proteinLogger by LazyLogger()

        fun fromID(id: String): Protein {
                //print("Please enter the PDB-ID: ")
                //val id = readLine()
            try { StructureIO.getStructure(id) }
            catch (struc: IOException) {
                proteinLogger.error("Something went wrong during the loading process of the pdb file, " +
                        "maybe a typo in the pdb entry or you chose a deprecated one?" +
                    "Here is the trace: \n" +
                struc.printStackTrace())
            }
            catch(struc: StructureException) {
                proteinLogger.error("Something went wrong during the loading of the pdb file."+
                "Here is the trace: \n" +
                struc.printStackTrace())
            }
            finally {
                val struc = StructureIO.getStructure(id)
                val protein = Protein(struc)
                return protein
            }
        }

        fun fromFile(path: String): Protein {
            val reader = PDBFileReader()
            //print("Please enter the path of your PDB-File: ")
            //val readPath = readLine()
            try { reader.getStructure(path) }
            catch (struc: InvalidPathException) {
                proteinLogger.info("Path was invalid, maybe this helps: ${struc.reason} " +
                    "or the index: ${struc.index}")
            }
            catch(struc: FileNotFoundException) {
                proteinLogger.error("The pdb file is not in the directory")
            }
            catch(struc: Exception) {
                proteinLogger.error("Something about the pdb file is wrong. This is not an invalid path problem nor is" +
                    "it a file-not-found-problem.")
            }

            finally {
                val struc = reader.getStructure(path)
                return Protein(struc)
            }
        }

    }
}
