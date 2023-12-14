package graphics.scenery.tests.examples.advanced

import graphics.scenery.*
import graphics.scenery.attribute.material.Material
import graphics.scenery.backends.Renderer
import graphics.scenery.numerics.Random
import graphics.scenery.schroedingerSmoke.ISF
import graphics.scenery.schroedingerSmoke.Particles
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.linear.ArrayRealVector
import org.joml.Vector3f
import kotlin.concurrent.thread

class SchroedingerSmokeSphere : SceneryBase("SchroedingerSmokeLeapfrog") {
    override fun init() {
        renderer = hub.add(Renderer.createRenderer(hub, applicationName, scene, 1024, 1024))

        val rowSize = 10f

        val ambient = AmbientLight()
        scene.addChild(ambient)

        val lightbox = Box(Vector3f(25.0f, 25.0f, 25.0f), insideNormals = true)
        lightbox.name = "Lightbox"
        lightbox.material {
            diffuse = Vector3f(0.1f, 0.1f, 0.1f)
            roughness = 1.0f
            metallic = 0.0f
            cullingMode = Material.CullingMode.None
        }
        lightbox.spatial().position = Vector3f(0f, 0f, 10f)
        scene.addChild(lightbox)
        val lights = (0 until 8).map {
            val l = PointLight(radius = 8.0f)
            l.spatial {
                position = Vector3f(
                    Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                    Random.randomFromRange(-rowSize/2.0f, rowSize/2.0f),
                    Random.randomFromRange(1.0f, 5.0f)
                )
            }
            l.emissionColor = Random.random3DVectorFromRange( 0.2f, 0.8f)
            l.intensity = Random.randomFromRange(0.2f, 0.8f)

            lightbox.addChild(l)
            l
        }

        lights.forEach { lightbox.addChild(it) }

        val stageLight = PointLight(radius = 35.0f)
        stageLight.name = "StageLight"
        stageLight.intensity = 0.5f
        stageLight.spatial {
            position = Vector3f(0.0f, 0.0f, 5.0f)
        }
        scene.addChild(stageLight)

        val cameraLight = PointLight(radius = 5.0f)
        cameraLight.name = "CameraLight"
        cameraLight.emissionColor = Vector3f(1.0f, 1.0f, 0.0f)
        cameraLight.intensity = 0.8f

        val cam: Camera = DetachedHeadCamera()
        cam.spatial {
            position = Vector3f(0.0f, 0.0f, 15.0f)
        }
        cam.perspectiveCamera(50.0f, windowWidth, windowHeight)
        scene.addChild(cam)

        cam.addChild(cameraLight)



        //setting up parameters for the Schroedinger smoke
        val size = Triple(10, 5, 5)
        val res = Triple (128, 64, 64)
        val hBar = 0.1
        val dt = 1/24.toDouble()

        val isf = ISF(size.first, size.second, size.third, res.first, res.second, res.third, hBar, dt)

        val tmax = 85
        val backgroundVel = Triple(-0.2, 0.0, 0.0)

        //radii and normal vectors of the circle
        val r1 = 1.5
        val r2 = 0.9
        val n1 = Triple(-0.1, 0.0, 0.0)
        val n2 = n1

        //position of the circles center
        val cen1 = Triple(size.first/2.0, size.second/2.0, size.third/2.0)
        val cen2 = cen1

        val numberOfParticles = 1000


        val uu = FloatArray(numberOfParticles) { Random.randomFromRange(0f, 1f) }
        val vv = FloatArray(numberOfParticles) { Random.randomFromRange(0f, 1f) }

        //visualizing
        val particleSphere = Icosphere(0.01f, 3)
        particleSphere.setMaterial(ShaderMaterial.fromFiles("DefaultDeferredInstanced.vert", "DefaultDeferred.frag")) {
            diffuse = Vector3f(0.9f, 0.0f, 0.02f)
            specular = Vector3f(0.05f, 0f, 0f)
            metallic = 0.01f
            roughness = 0.9f
        }
        particleSphere.name = "particleSphere_Master"
        val particleSphereInstanced = InstancedNode(particleSphere)
        scene.addChild(particleSphereInstanced)


        val particles = Particles(numberOfParticles)

        particles.y = ArrayRealVector(DoubleArray(numberOfParticles) {  0.5 + 4 * uu[it] })
        particles.z = ArrayRealVector(DoubleArray(numberOfParticles) { (0.5 + 4 * vv[it]) })
        particles.x = ArrayRealVector(DoubleArray(numberOfParticles) { 5.0 })

        for( i  in 0 until numberOfParticles) {
            val particlePosition = Vector3f(particles.x.getEntry(i).toFloat(),
                particles.y.getEntry(i).toFloat(), particles.z.getEntry(i).toFloat())
            particles.positions.add(particlePosition)
            val p = particleSphereInstanced.addInstance()
            p.name = "particle_${i}"
            p.parent = particles
            p.spatial {
                position = particlePosition
            }
        }

        scene.addChild(particles)


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

        val d = isf.dx * 5  // Thickness around the disk

        // Initialize psi1 and psi2
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
                    Complex(0.0, phase[i][j][k]).exp().multiply(0.01)
                }
            }
        }

        // Add vortex rings to psi1
        val psi1WithVortex1 = isf.addCircle(initPsi1, cen1, n1, r1, d, isf.dx, isf.dy, isf.dz)
        val psi1WithVortex2 = isf.addCircle(psi1WithVortex1, cen2, n2, r2, d, isf.dx, isf.dy, isf.dz)


        val initNormalPsi = ISF.normalize(Pair(psi1WithVortex2, initPsi2))
        var projectedPsi = isf.pressureProject(initNormalPsi)


        thread {
            while(!sceneInitialized()) {
                Thread.sleep(200)
            }
            val itermax = (tmax / dt).toInt()
            for (iter in 1..itermax) {

                val normalizedPsi = ISF.normalize(
                    Pair(
                        isf.schroedingerFlow(projectedPsi.first),
                        isf.schroedingerFlow(projectedPsi.second)
                    )
                )

                projectedPsi = isf.pressureProject(normalizedPsi)

                // Particle visualization and updating
                val (vx, vy, vz) = isf.velocityOneForm(projectedPsi.first, projectedPsi.second, isf.hBar)
                val (sharpVx, sharpVy, sharpVz) = isf.staggeredSharp(vx, vy, vz)
                particles.staggeredAdvect(isf, sharpVx, sharpVy, sharpVz, isf.dt)
                particles.positions.forEachIndexed { index, position ->
                    particleSphereInstanced.instances[index].spatial().position = position
                }

                /*
                val indicesToKeep = particles.getIndicesWithinBounds(size)
                particles.keep(indicesToKeep)
                particleSphereInstanced.instances.indices.filter { !indicesToKeep.contains(it) }.forEach { instanceIndex ->
                    particleSphereInstanced.instances[instanceIndex].visible = false
                }
                 */

                // Update particle positions and apply boundary conditions
                // Placeholder for particle position updates and boundary conditions

                // Visualization and drawing (handled by your own framework)
                // Placeholder for visualization updates
                Thread.sleep(5)
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SchroedingerSmokeSphere().main()
        }
    }
}
