package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.schroedingerSmoke.ISF
import graphics.scenery.schroedingerSmoke.Particles
import graphics.scenery.tests.examples.basic.TexturedCubeExample
import graphics.scenery.textures.Texture
import graphics.scenery.utils.Image
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.linear.ArrayRealVector
import org.joml.Vector3f

class SchroedingerSmokeSphere : SceneryBase("SchroedingerSmokeLeapfrog") {
    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 1024, 1024))

        val cam: Camera = DetachedHeadCamera()
        with(cam) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 2.5f)
            }
            perspectiveCamera(70.0f, windowWidth, windowHeight)

            targeted = true
            target = Vector3f(0.0f, 0.0f, 0.0f)

            scene.addChild(this)
        }

        val camlight = PointLight(3.0f)
        camlight.intensity = 5.0f
        cam.addChild(camlight)

        val box = Box(Vector3f(1.0f, 1.0f, 1.0f))

        with(box) {
            spatial {
                position = Vector3f(0.0f, 0.0f, 0.0f)
            }
            material {
                ambient = Vector3f(1.0f, 0.0f, 0.0f)
                diffuse = Vector3f(0.0f, 1.0f, 0.0f)
                textures["diffuse"] = Texture.fromImage(Image.fromResource("textures/helix.png", TexturedCubeExample::class.java))
                specular = Vector3f(1.0f, 1.0f, 1.0f)
            }

            scene.addChild(this)
        }

        val lights = (0..2).map {
            PointLight(radius = 15.0f)
        }.map { light ->
            light.spatial {
                position = Random.random3DVectorFromRange(-3.0f, 3.0f)
            }
            light.emissionColor = Random.random3DVectorFromRange(0.2f, 0.8f)
            light.intensity = Random.randomFromRange(0.1f, 0.8f)
            light
        }

        val floor = Box(Vector3f(500.0f, 0.05f, 500.0f))
        floor.spatial {
            position = Vector3f(0.0f, -1.0f, 0.0f)
        }
        floor.material {
            diffuse = Vector3f(1.0f, 1.0f, 1.0f)
        }
        scene.addChild(floor)

        lights.forEach(scene::addChild)



        //setting up parameters for the Schroedinger smoke
        val res = Triple(10, 5, 5)
        val size = Triple (64, 64, 64)
        val hBar = 0.1
        val dt = 1/24.toDouble()

        val isf = ISF(size.first, size.second, size.third, res.first, res.second, res.third, hBar, dt)

        val tmax = 85
        val backgroundVel = Triple(-0.2, 0.0, 0.0)

        //radii and normal vectors of the circle
        val r1 = 1.5
        val r2 = 0.9
        val n1 = Triple(-0.1, 0.0, 0.0)
        val n2 = Triple(-0.1, 0.0, 0.0)

        //position of the circles center
        val cen1 = Vector3f(size.first.toFloat()/2, size.second.toFloat()/2, size.third.toFloat()/2)
        val cen2 = cen1

        //TODO make this dependent on the pointcloud
        val numberOfParticles = 10000


        val uu = FloatArray(numberOfParticles) { Random.randomFromRange(0f, 1f) }
        val vv = FloatArray(numberOfParticles) { Random.randomFromRange(0f, 1f) }
        val particles = Particles()
        particles.y = ArrayRealVector(DoubleArray(numberOfParticles) { 0.5 + 4 * uu[it] })
        particles.z = ArrayRealVector(DoubleArray(numberOfParticles) { 0.5 + 4 * vv[it] })
        particles.x = ArrayRealVector(DoubleArray(numberOfParticles) { 5.0 })


        // Calculate kvec by scaling background_vel with hbar
        val kvec = Triple(backgroundVel.first / hBar, backgroundVel.second / hBar,
            backgroundVel.third / hBar)

        // Calculate phase as a dot product-like operation
        val phase = Array(isf.px.size) { i ->
            Array(isf.px[i].size) { j ->
                Array(isf.px[i][j].size) { k ->
                    kvec.first * isf.px[i][j][k] + kvec.second * isf.py[i][j][k] + kvec.third * isf.pz[i][j][k]
                }
            }
        }

        val initPsi1 = Array(phase.size) { i ->
            Array(phase[i].size) { j ->
                Array(phase[i][j].size) { k ->
                    Complex(0.0, phase[i][j][k]).exp()
                }
            }
        }

        val initPsi2 = Array(phase.size) { i ->
            Array(phase[i].size) { j ->
                Array(phase[i][j].size) { k ->
                    Complex(0.0, 0.01 * phase[i][j][k]).exp()
                }
            }
        }

        val initNormalPsi = ISF.normalize(Pair(initPsi1, initPsi2))
        var projectedPsi = isf.pressureProject(initNormalPsi)

        val itermax = (tmax / dt).toInt()
        for (iter in 1..itermax) {
            val t = iter * dt

            val normalizedPsi = ISF.normalize(Pair(isf.schroedingerFlow(projectedPsi.first),
                isf.schroedingerFlow(projectedPsi.second)))

            projectedPsi = isf.pressureProject(normalizedPsi)

            // Particle visualization and updating
            val (vx, vy, vz) = isf.velocityOneForm(projectedPsi.first, projectedPsi.second, isf.hBar)
            val (sharpVx, sharpVy, sharpVz) = isf.staggeredSharp(vx, vy, vz)
            particles.staggeredAdvect(isf, sharpVx, sharpVy, sharpVz, isf.dt)

            // Update particle positions and apply boundary conditions
            // Placeholder for particle position updates and boundary conditions

            // Visualization and drawing (handled by your own framework)
            // Placeholder for visualization updates
        }



    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SchroedingerSmokeSphere().main()
        }
    }
}
