package graphics.scenery

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    val elementList = ArrayList<ChemicalElement>()

    init {
        val mapper = jacksonObjectMapper()
        val file = FileReader("src/main/resources/graphics/scenery/proteins/PubChemElements_all.json").readText()
        val periodicTable = mapper.readValue(file, PeriodicTableau::class.java)
    }

    fun extractDataTypes(periodicTable: PeriodicTableau) {
        periodicTable.table.row.forEach { pureStringElement ->
            val elementNumber = pureStringElement.cell[0].toInt()
            val symbol = pureStringElement.cell[1]
            val name = pureStringElement.cell[2]
            val atomicMass = pureStringElement.cell[3].toFloat()
            val color: Vector3f //TODO
            val electronConfiguration = pureStringElement.cell[5]
            val electroNegativity = pureStringElement.cell[6].toFloat()
            val atomicRadius = pureStringElement.cell[7].toFloat()
            val ionizationEnergy = pureStringElement.cell[8].toFloat()
            val electronAffinity = pureStringElement.cell[9].toFloat()
            val oxidationStates = pureStringElement.cell[10]
            val standardState = pureStringElement.cell[11]
            val meltingPoint = pureStringElement.cell[12].toFloat()
            val boilingPoint = pureStringElement.cell[13].toFloat()
            val density = pureStringElement.cell[14].toFloat()
            val groupBlock = pureStringElement.cell[15]
            val yearDiscovered = pureStringElement.cell[16]

        }
    }
}
