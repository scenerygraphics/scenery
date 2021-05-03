package graphics.scenery

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphics.scenery.utils.LazyLogger
import org.joml.Vector3f
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList

/**
 *
 */
class PeriodicTable {

    //data-classes to store the JsonProperties
    data class ChemCell(@JsonProperty("Cell") val cell: ArrayList<String>)
    data class ChemColumn(@JsonProperty("Column") val column: ArrayList<String>)
    data class ChemTable(@JsonProperty("Columns") val columns: ChemColumn,@JsonProperty("Row") val row: ArrayList<ChemCell>)
    data class PeriodicTableau(@JsonProperty("Table") val table: ChemTable)

    private val elementList = ArrayList<ChemicalElement>()
    private val logger by LazyLogger()

    init {
        val mapper = jacksonObjectMapper()
        val file = FileReader("src/main/resources/graphics/scenery/proteins/PubChemElements_all.json").readText()
        val periodicTable = mapper.readValue(file, PeriodicTableau::class.java)
        extractDataTypes(periodicTable)
    }

    private fun extractDataTypes(periodicTable: PeriodicTableau) {
        periodicTable.table.row.forEach { pureStringElement ->
            //color is saved in Hex-Format in Json file and needs a bit more work
            val color = Vector3f()
            val colorString = pureStringElement.cell[4].chunked(2).forEachIndexed { index, co ->
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

    fun findElementByNumber(elementNumber: Int): ChemicalElement {
        return if(elementNumber >= 1 && elementNumber <= elementList.size) {
            elementList[elementNumber - 1]
        } else {
            logger.warn("The requested element number is not in the periodic table. " +
                    "You will receive hydrogen as a default")
            elementList[0]
        }
    }
}
