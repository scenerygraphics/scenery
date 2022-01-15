package graphics.scenery.flythroughs

import graphics.scenery.Box
import graphics.scenery.Camera
import graphics.scenery.Scene
import graphics.scenery.attribute.spatial.Spatial
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole

import graphics.scenery.proteins.RibbonDiagram
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.joml.Quaternionf
import org.joml.Vector3f
import org.scijava.ui.behaviour.ClickBehaviour

class ProteinBuilder(ribbonDiagram: RibbonDiagram, override val cam: ()-> Camera?, val scene: Scene,
                     private val proteinName: String, private val hmd: OpenVRHMD? = null, val controller: Spatial? = null): ProteinRollercoaster(ribbonDiagram, cam), ClickBehaviour {

    // ribbon diagram to work with the residues later on
    private val ribbonDiagram = ribbonDiagram
    // hash map of all amino acid abbreviations as well as their full name and chemical category (acidic, basic, etc.)
    val abbreviations = IUPACAbbreviationsReader().abbrevations
    // the number of points the camera wanders through is not clearly linked to the numbers of residues, hence, we'll divide it by the number of residues
    private val cameraFramesPerStepFloat = listOfCameraFrames.size.toFloat()/ribbonDiagram.children.flatMap { chains -> chains.children }.flatMap { residues -> residues.children }.size
    //toInt() cuts away all the decimal places after 0
    private val cameraFramesPerStep = cameraFramesPerStepFloat.toInt()
    //for more precision we will use the first decimal place after the comma as well
    private val remainder = ((cameraFramesPerStepFloat-cameraFramesPerStep)*10).toInt()
    //counting variable to keep track of the current residue
    var k = 0
    var currentAminoCode: String? = ribbonDiagram.protein.structure.chains.flatMap { it.atomGroups }.filter { it.isAminoAcid }[k].pdbName
    // all images of amino acids
    private val allImages = fillUpImageMap(abbreviations)
    override fun click(x: Int, y: Int) {
        if(k > 0) {
            val startingPoint = if(k%10 <= remainder) { 0 } else { 1 }
            for (l in  startingPoint .. cameraFramesPerStep) {
                flyToNextPoint()
            }
        }
        if (k <= (ribbonDiagram.protein.structure.chains.flatMap { it.atomGroups }.filter { it.isAminoAcid }.lastIndex)) {
            currentAminoCode =
                ribbonDiagram.protein.structure.chains.flatMap { it.atomGroups }.filter { it.isAminoAcid }[k].pdbName
            val aaImage = allImages[ribbonDiagram.protein.structure.chains.flatMap { it.atomGroups }
                .filter { it.isAminoAcid }[k].pdbName]
            //remove old pic
            scene.removeChild("le box du win")
            //add the amino acid picture
            val box = Box(Vector3f(1.0f, 1.0f, 1.0f))
            box.name = "le box du win"
            val height = aaImage?.height
            val width = aaImage?.width
            if (width != null && height != null) {
                box.spatial().scale = Vector3f((width / height.toFloat())/5f, 1/5f, 0f)
            }
            box.spatial {
                //VR mode, baby!
                if(hmd != null) {
                    position = controller!!.worldPosition()
                }
                //nope? okay, lets go for 2D then
                else {
                    rotation = Quaternionf(camera?.spatial()?.rotation).conjugate()
                    position = Vector3f(camera?.spatial()?.position)
                    val forwardTimesTwo = Vector3f()
                    if (camera?.targeted == true) {
                        box.spatial().position.add(camera.target.mul(2f, forwardTimesTwo))
                    } else {
                        box.spatial().position.add(camera?.forward?.mul(2f, forwardTimesTwo))
                    }
                }
            }
            if(hmd != null) {
                box.update.add {
                    box.spatial {
                        rotation = Quaternionf(hmd.getOrientation()).conjugate().normalize()
                        position = controller!!.worldPosition()
                    }
                }
            }
            box.material {
                if (aaImage != null) {
                    textures["diffuse"] = Texture.fromImage(aaImage)
                }
            }
            scene.addChild(box)
            //todo add a unique identifier for the ribon diagram
                if (k <= scene.children.filter { it is RibbonDiagram }[0].children.flatMap { subProtein -> subProtein.children }
                        .flatMap { curve -> curve.children }.lastIndex) {
                    scene.children.filter { it is RibbonDiagram }[0].children.flatMap { subProtein -> subProtein.children }
                        .flatMap { curve -> curve.children }[k].visible = true
                }
        }
        k += 1
    }

    companion object LoadImages {
        fun fillUpImageMap(abbreviations: HashMap<String, IUPACAbbreviationsReader.IUPACAbbrevation>): HashMap<String, Image> {
            val images = HashMap<String, Image>(20)
            abbreviations.forEach { aminoAcid ->
                val chemicalCategory = aminoAcid.value.chemicalCategory.toString().lowercase()
                images[aminoAcid.key] = Image.fromResource("${chemicalCategory}/${aminoAcid.value.fullName}.png", ProteinBuilder::class.java)
            }
            return images
        }

        fun createAndSet(ribbon: RibbonDiagram, scene: Scene, proteinName: String, hmd: OpenVRHMD, button: List<OpenVRHMD.OpenVRButton>,
                         controllerSide: List<TrackerRole>){
            hmd.events.onDeviceConnect.add { _, device, _ ->
                if (device.type == TrackedDeviceType.Controller) {
                    if(controllerSide.contains(device.role)) {
                        device.model?.let { controller ->
                            val proteinBuilder = ProteinBuilder(
                                ribbon, { scene.activeObserver }, scene, proteinName, hmd,
                                controller.children.first().spatialOrNull()
                                    ?: throw IllegalArgumentException("The target controller needs a spatial.")
                            )
                            val name = "ProteinBuilder"
                            hmd.addBehaviour(name, proteinBuilder)
                            button.forEach {
                                hmd.addKeyBinding(name, device.role, it)
                            }
                        }
                    }
                }
            }
        }
    }
}