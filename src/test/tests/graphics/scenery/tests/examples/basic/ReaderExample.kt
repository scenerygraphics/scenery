package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.controls.OpenVRHMD
import graphics.scenery.controls.TrackedDeviceType
import graphics.scenery.controls.TrackerRole
import graphics.scenery.controls.behaviours.ControllerDrag
import graphics.scenery.numerics.Random
import graphics.scenery.volumes.Volume
import org.junit.Test
import org.scijava.Context
import org.scijava.ui.UIService
import org.scijava.ui.behaviour.ClickBehaviour
import org.scijava.widget.FileWidget
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread
import kotlin.streams.toList

/**
 * Example for loading geometry and volumetric files.
 *
 * @author Ulrik Günther <hello@ulrik.is>
 */
class ReaderExample : SceneryBase("ReaderExample", 1280, 720) {
    var hmd: OpenVRHMD? = null
    lateinit var loadedFilename: String
    lateinit var loadedObject: Node

    var playing = false
    var delay = 500L

    override fun init() {
        val files = ArrayList<String>()

        val c = Context()
        val ui = c.getService(UIService::class.java)
        val file = ui.chooseFile(null, FileWidget.OPEN_STYLE)
        files.add(file.absolutePath)

        val cam = DetachedHeadCamera()
        hmd = try {
            OpenVRHMD()
        } catch (e: Exception) {
            null
        }

        hmd?.let {
            hub.add(it)
            cam.tracker = it
        }

        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, windowWidth, windowHeight))

        val b = Box(GLVector(50.0f, 0.2f, 50.0f))
        b.position = GLVector(0.0f, -1.0f, 0.0f)
        b.material.diffuse = GLVector(0.1f, 0.1f, 0.1f)
        scene.addChild(b)

        val tetrahedron = listOf(
            GLVector(1.0f, 0f, -1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(-1.0f,0f,-1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,1.0f,1.0f/Math.sqrt(2.0).toFloat()),
            GLVector(0.0f,-1.0f,1.0f/Math.sqrt(2.0).toFloat()))

        val lights = (0 until 4).map { PointLight(radius = 50.0f) }

        loadedObject = if(files.isNotEmpty()) {
            when {
                files.first().endsWith(".tiff") || files.first().endsWith(".tif") -> {
                    val v = Volume()
                    v.readFrom(Paths.get(files.first()))

                    v
                }
                files.first().endsWith(".raw") -> {
                    val v = Volume()
                    v.readFromRaw(Paths.get(files.first()))

                    v
                }

                else -> {
                    val m = Mesh()
                    m.readFrom(files.first())

                    m
                }
            }
        } else {
            throw IllegalStateException("No file selected")
        }

        loadedFilename = files.first()
        loadedObject.fitInto(6.0f, scaleUp = false)

        scene.addChild(loadedObject)

        val bg = BoundingGrid()
        bg.node = loadedObject

        tetrahedron.mapIndexed { i, position ->
            lights[i].position = position * 5.0f
            lights[i].emissionColor = Random.randomVectorFromRange(3, 0.8f, 1.0f)
            lights[i].intensity = 0.5f
            scene.addChild(lights[i])
        }

        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, windowWidth.toFloat(), windowHeight.toFloat())
            active = true

            scene.addChild(this)
        }

        thread {
            while(!loadedObject.initialized) {
                Thread.sleep(200)
            }

            loadedObject.putAbove(GLVector(0.0f, -0.3f, 0.0f))

            hmd?.events?.onDeviceConnect?.add { hmd, device, timestamp ->
                if(device.type == TrackedDeviceType.Controller) {
                    logger.info("Got device ${device.name} at $timestamp")
                    device.model?.let { hmd.attachToNode(device, it, cam) }
                }
            }
        }
    }

    override fun inputSetup() {
        val cam = scene.findObserver() ?: throw IllegalStateException("Could not find camera")
        val hmd = this.hmd ?: return

        inputHandler?.addBehaviour("nextObject", ClickBehaviour { _, _ -> loadNext(forward = true) })
        inputHandler?.addBehaviour("previousObject", ClickBehaviour { _, _ -> loadNext(forward = false) })
        inputHandler?.addKeyBinding("nextObject", "shift M")
        inputHandler?.addKeyBinding("previousObject", "shift N")

        inputHandler?.let { handler ->
            hashMapOf(
                "move_forward_fast" to "K",
                "move_back_fast" to "J",
                "move_left_fast" to "H",
                "move_right_fast" to "L").forEach { (name, key) ->
                handler.getBehaviour(name)?.let { b ->
                    hmd.addBehaviour(name, b)
                    hmd.addKeyBinding(name, key)
                }
            }
        }

        val nextTimepoint = ClickBehaviour { _, _ ->
            thread {
                loadNext(true)
            }
        }

        val prevTimepoint = ClickBehaviour { _, _ ->
            thread {
                loadNext(false)
            }
        }

        hmd.addBehaviour("skip_to_next", nextTimepoint)
        hmd.addKeyBinding("skip_to_next", "D")
        hmd.addBehaviour("skip_to_prev", prevTimepoint)
        hmd.addKeyBinding("skip_to_prev", "A")

        val scaleFactor = 1.2f
        val fasterOrScale = ClickBehaviour { _, _ ->
            if(playing) {
                delay = minOf((delay / scaleFactor).toLong(), 2000L)
                cam.showMessage("Speed: ${String.format("%.2f", (1000f/delay.toFloat()))} vol/s")
            } else {
                val scale = minOf(loadedObject.scale.x() * 1.2f, 3.0f)
                loadedObject.scale = GLVector.getOneVector(3) * scale
            }
        }

        hmd.addBehaviour("faster_or_scale", fasterOrScale)
        hmd.addKeyBinding("faster_or_scale", "W")

        val slowerOrScale = ClickBehaviour { _, _ ->
            if(playing) {
                delay = maxOf((delay * scaleFactor).toLong(), 5L)
                cam.showMessage("Speed: ${String.format("%.2f", (1000f/delay.toFloat()))} vol/s")
            } else {
                val scale = maxOf(loadedObject.scale.x() / 1.2f, 0.1f)
                loadedObject.scale = GLVector.getOneVector(3) * scale
            }
        }

        hmd.addBehaviour("slower_or_scale", slowerOrScale)
        hmd.addKeyBinding("slower_or_scale", "S")

        val playPause = ClickBehaviour { _, _ ->
            playing = !playing
            if(playing) {
                cam.showMessage("Paused")
            } else {
                cam.showMessage("Playing")
            }
        }

        hmd.addBehaviour("play_pause", playPause)
        hmd.addKeyBinding("play_pause", "M")

        val move = ControllerDrag(
            TrackerRole.RightHand,
            hmd,
            trackPosition = true,
            trackRotation = true
        ) { loadedObject }

        hmd.addBehaviour("trigger_move", move)
        hmd.addKeyBinding("trigger_move", "U")

        hmd.allowRepeats += OpenVRHMD.OpenVRButton.Trigger to TrackerRole.RightHand
    }

    /**
     * Loads the next dataset with the same extension from the directory the current
     * dataset resides in. If [forward] is true, the direction is forward, otherwise backwards.
     */
    fun loadNext(forward: Boolean = true) {
        val extension = loadedFilename.substringAfterLast(".").toLowerCase()
        val current = Paths.get(loadedFilename)

        val direction = if(forward) {
            1
        } else {
            -1
        }

        logger.info("Getting next for $current with $extension in directory ${current.parent}")
        val files = Files.list(current.parent).toList().filter { it.toFile().absolutePath.endsWith(extension) }
        logger.info("Files are ${files.joinToString()}")
        val currentIndex = files.indexOf(current)
        val newIndex = if(currentIndex + direction < 0) {
            files.size - 1
        } else {
            (currentIndex + direction) % files.size
        }
        val file = files.get(newIndex)


        when(extension) {
            "tif", "tiff" -> (loadedObject as? Volume)?.readFrom(file)
            "raw" -> (loadedObject as? Volume)?.readFromRaw(file)
            "obj", "stl" -> {
                loadedObject = Mesh()
                (loadedObject as? HasGeometry)?.readFrom(file.toFile().absolutePath)
                loadedObject.centerOn(GLVector.getNullVector(3))
                loadedObject.fitInto(6.0f, scaleUp = false)
            }
        }

        loadedFilename = file.toFile().absolutePath
    }

    @Test override fun main() {
        super.main()
    }
}
