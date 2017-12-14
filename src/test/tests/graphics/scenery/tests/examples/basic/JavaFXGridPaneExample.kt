package graphics.scenery.tests.examples.basic

import cleargl.GLVector
import com.sun.javafx.application.PlatformImpl
import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.utils.SceneryPanel
import javafx.application.Platform
import javafx.beans.property.SimpleDoubleProperty
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.geometry.VPos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.TextAlignment
import javafx.stage.Stage
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * TexturedCubeExample, embedded in a JavaFX window
 *
 * @author Ulrik Günther <hello@ulrik.is>
 * @author Hongkee Moon <moon@mpi-cbg.de>
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hmmi.org>
 */
class JavaFXGridPaneExample : SceneryBase("JavaFXGridPaneExample", windowWidth = 512, windowHeight = 512) {
    override fun init() {
        val latch = CountDownLatch(1)
        val imagePanel = SceneryPanel(windowWidth, windowHeight)
        val pane = GridPane()

        val initialWidth = SimpleDoubleProperty()
        val initialHeight = SimpleDoubleProperty()

        PlatformImpl.startup { }

        Platform.runLater {
            val stage = Stage()
            stage.title = applicationName


            pane.add(imagePanel, 0, 0 )

            initialWidth.bind(pane.widthProperty().divide(2))
            initialHeight.bind(pane.heightProperty().divide(2))

            val cc1 = ColumnConstraints()
            cc1.percentWidth = 50.0
            val cc2 = ColumnConstraints()
            cc2.percentWidth = 50.0
            val rc1 = RowConstraints()
            rc1.percentHeight = 50.0
            val rc2 = RowConstraints()
            rc2.percentHeight = 50.0

            pane.columnConstraints.setAll( cc1, cc2 )
            pane.rowConstraints.setAll( rc1, rc2 )

            val img = Image( "https://github.com/hanslovsky/imglyb-examples/raw/master/resources/butterfly_small.jpg" );
            val p = Pane()
            val v = ImageView(img)
            v.fitWidthProperty().bind(p.widthProperty())
            v.fitHeightProperty().bind(p.heightProperty())
            p.children.addAll(v)

            pane.add( p, 1, 1)

            val scene = Scene(pane, windowWidth.toDouble(), windowHeight.toDouble())
            stage.scene = scene
            stage.onCloseRequest = EventHandler {
                renderer?.shouldClose = true

                Platform.runLater { Platform.exit() }
            }
            stage.show()


            latch.countDown()

        }

        latch.await()



        renderer = Renderer.createRenderer(hub, applicationName, scene, initialWidth.get().toInt(), initialHeight.get().toInt(), embedIn = imagePanel)
        hub.add(SceneryElement.Renderer, renderer!!)

        val boxmaterial = Material()
        with(boxmaterial) {
            ambient = GLVector(1.0f, 0.0f, 0.0f)
            diffuse = GLVector(0.0f, 1.0f, 0.0f)
            specular = GLVector(1.0f, 1.0f, 1.0f)
            textures.put("diffuse", TexturedCubeExample::class.java.getResource("textures/helix.png").file)
        }

        val box = Box(GLVector(1.0f, 1.0f, 1.0f))
        box.name = "le box du win"

        with(box) {
            box.material = boxmaterial
            scene.addChild(this)
        }

        val lights = (0..2).map {
            PointLight()
        }

        lights.mapIndexed { i, light ->
            light.position = GLVector(2.0f * i, 2.0f * i, 2.0f * i)
            light.emissionColor = GLVector(1.0f, 0.0f, 1.0f)
            light.intensity = 500.2f * (i + 1)
            scene.addChild(light)
        }

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            position = GLVector(0.0f, 0.0f, 5.0f)
            perspectiveCamera(50.0f, 512.0f, 512.0f)
            active = true

            scene.addChild(this)
        }

        thread {
            while (true) {
                box.rotation.rotateByAngleY(0.01f)
                box.needsUpdate = true

                Thread.sleep(20)
            }
        }
    }

    @Test override fun main() {
        super.main()
    }
}
