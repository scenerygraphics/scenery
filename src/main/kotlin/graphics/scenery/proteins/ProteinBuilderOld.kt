package graphics.scenery.proteins

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import graphics.scenery.Camera

class ProteinBuilder(val cam: () -> Camera?) {

    data class IUPACAbbrevation(val singleLetter: Char, val threeLetters: String, val fullName: String)

    enum class ChemicalCategory {Acid, Basic, Hydrophobic, Polar}

    val abbrevations = ArrayList<IUPACAbbrevation>(20)
    data class AACell(@JsonProperty("Cell") val cell: ArrayList<String>)
    data class AAColumn(@JsonProperty("Column") val column: ArrayList<String>)
    data class AATable(@JsonProperty("Columns") val columns: AAColumn, @JsonProperty("Row") val row: ArrayList<AACell>)
    data class IUPACAbbreviations(@JsonProperty("Table") val table: AATable)

    init {
        //parsing the json file
        val mapper = jacksonObjectMapper()
        val file = this::class.java.getResource("IUPACAminoAcidAbbreviations.json").readText()
        val iupacAbbreviations = mapper.readValue(file,ProteinBuilder.IUPACAbbreviations::class.java)
        extractInformation(iupacAbbreviations)
    }

    fun extractInformation(abbreviationsFromJson: IUPACAbbreviations) {
        abbreviationsFromJson.table.row.forEach { stringAbbreviation ->
            val abbreviation = IUPACAbbrevation(
                singleLetter = stringAbbreviation.cell[0][0],
                threeLetters = stringAbbreviation.cell[1],
                fullName = stringAbbreviation.cell[2]
            )
            abbrevations.add(abbreviation)
        }
    }

}
