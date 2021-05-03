package graphics.scenery

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphics.scenery.utils.LazyLogger
import org.joml.Vector3f
import java.io.FileReader
import kotlin.collections.ArrayList

/**
 * Class to parse the periodic table generously provided by PubChem as a json-file (https://pubchem.ncbi.nlm.nih.gov/periodic-table/).
 * We store all elements as a [ChemicalElement] and put them into the [elementList].
 */
open class PeriodicTable {

    /*
    data-classes to store the JsonProperties
    The json file has the following format:
    {Table: {
        Columns: {
            Column: [ "Here are the names of all the parameters" ]
            }
        Row: [
            { Cell: [ "Here is the information about the first element stored" ] },
            { Cell: [ Here is the information about the second element stored" ] }, ... ]
        }
     }
     */
    data class ChemCell(@JsonProperty("Cell") val cell: ArrayList<String>)
    data class ChemColumn(@JsonProperty("Column") val column: ArrayList<String>)
    data class ChemTable(@JsonProperty("Columns") val columns: ChemColumn,@JsonProperty("Row") val row: ArrayList<ChemCell>)
    data class PeriodicTableau(@JsonProperty("Table") val table: ChemTable)

    val elementList = ArrayList<ChemicalElement>()
    private val logger by LazyLogger()

    init {
        //parsing the json file
        val mapper = jacksonObjectMapper()
        val file = FileReader("src/main/resources/graphics/scenery/proteins/PubChemElements_all.json").readText()
        val periodicTable = mapper.readValue(file, PeriodicTableau::class.java)

        //extracting the information in the right data format
        extractDataTypes(periodicTable)
    }

    /**
     * Takes the element information stored as Strings, converts it to the right format, afterwards, adding it to the list
     */
    private fun extractDataTypes(periodicTable: PeriodicTableau) {
        periodicTable.table.row.forEach { pureStringElement ->
            //color is saved in Hex-Format in Json file and needs a bit more work
            val color = Vector3f()
            pureStringElement.cell[4].chunked(2).forEachIndexed { index, co ->
                val colorValue = co.toInt(16).toFloat()
                when (index) {
                    0 -> color.x = colorValue
                    1 -> color.y = colorValue
                    2 -> color.z = colorValue
                }
            }
            val element = ChemicalElement(
            pureStringElement.cell[0].toInt(),
            pureStringElement.cell[1],
            pureStringElement.cell[2],
            pureStringElement.cell[3].toFloat(),
            color,
            pureStringElement.cell[5],
            pureStringElement.cell[6].toFloatOrNull(),
            pureStringElement.cell[7].toFloatOrNull(),
            pureStringElement.cell[8].toFloatOrNull(),
            pureStringElement.cell[9].toFloatOrNull(),
            pureStringElement.cell[10],
            pureStringElement.cell[11],
            pureStringElement.cell[12].toFloatOrNull(),
            pureStringElement.cell[13].toFloatOrNull(),
            pureStringElement.cell[14].toFloatOrNull(),
            pureStringElement.cell[15],
            pureStringElement.cell[16]
            )
            elementList.add(element)
        }
    }

    /**
     * Finds an element by its elementNumber which corresponds to the index in the list plus one
     */
    fun findElementByNumber(elementNumber: Int): ChemicalElement {
        return when {
            elementNumber < 1 -> {
                logger.warn("The requested elementNumber is zero or negative." +
                " You will receive hydrogen as a default.")
                elementList[0] }
            elementNumber > elementList.size -> {
                logger.warn("The requested element number is not in the periodic table. " +
                    "You will receive hydrogen as a default")
                elementList[0]
            }
            else -> { elementList[elementNumber - 1] }
        }
    }

    /**
     * Finds an element by its symbol.
     */
    fun findElementBySymbol(elementSymbol: String): ChemicalElement {
        elementList.forEach { if(it.symbol == elementSymbol) {return it} }
        logger.warn("Symbol was not found in the list, the function will return hydrogen instead")
        return elementList[0]
    }
}
