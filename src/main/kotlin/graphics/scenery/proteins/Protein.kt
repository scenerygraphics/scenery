package graphics.scenery.proteins

import graphics.scenery.geometry.Mesh
import org.biojava.nbio.structure.*
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

    companion object MyProtein {

        fun fromID(id: String): Protein {
                //print("Please enter the PDB-ID: ")
                //val id = readLine()
            try { StructureIO.getStructure(id) }
            catch (struc: IOException) {
                print("Something went wrong in the loading process, " +
                        "maybe a typo in the pdb entry or you chose a deprecated one?")
                struc.printStackTrace()
            }
            catch(struc: StructureException) {
                print("Something went wrong with the loading.")
                struc.printStackTrace()
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
                print("Path was invalid, maybe this helps: ${struc.reason} " +
                    "or the index: ${struc.index}")
            }
            catch(struc: FileNotFoundException) {
                print("The File is not in the directory")
            }
            catch(struc: Exception) {
                print("Something went wrong, sorry!")
            }

            finally {
                val struc = reader.getStructure(path)
                return Protein(struc)
            }
        }

    }
}
