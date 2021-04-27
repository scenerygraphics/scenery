package graphics.scenery

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class PeriodicTable {
    init {
        data class ChemicalElement(@JsonProperty("AtomicNumber") val atomicNumber: Int,
                                   @JsonProperty("Symbol") val symbol: String,
                                   @JsonProperty("Name") val name: String,
                                   @JsonProperty("AtomicMass")val atomicMass: Float,
                                   @JsonProperty("CPKHexColor") val color: String,
                                   @JsonProperty("ElectronConfiguration") val electronConfiguration: String,
                                   @JsonProperty("Electronegativity") val electroNegativity: Float,
                                   @JsonProperty("AtomicRadius") val atomicRadius: Float,
                                   @JsonProperty("IonizationEnergy") val ionizationEnergy: Float,
                                   @JsonProperty("ElectronAffinity") val electronAffinity: Float,
                                   @JsonProperty("OxidationStates") val oxidationStates: String,
                                   @JsonProperty("StandardState") val standardState: String,
                                   @JsonProperty("MeltingPoint") val meltingPoint: Float,
                                   @JsonProperty("BoilingPoint") val boilingPoint: Float,
                                   @JsonProperty("Density") val density: Float,
                                   @JsonProperty("GroupBlock") val groupBlock: String,
                                   @JsonProperty("YearDiscovered")val yearDiscovered: String)
        data class ChemCell(val cell: Map<String, ChemicalElement>)
        data class ChemTable(val column: Map<String, ArrayList<String>>, val row: ArrayList<ChemCell>)
        data class PeriodicTableau(val thumbnail: Map<String?, ChemTable>)

        val mapper = jacksonObjectMapper()
        val file = FileReader("src/main/resources/graphics/scenery/proteins/PubChemElements_all.json").readText()
        print(file)
        val periodicTable = mapper.readValue(file, PeriodicTableau::class.java)
        print(periodicTable)
    }
}
