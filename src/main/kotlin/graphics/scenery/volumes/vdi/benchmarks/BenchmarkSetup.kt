package graphics.scenery.volumes.vdi.benchmarks

import graphics.scenery.Camera
import graphics.scenery.utils.lazyLogger
import graphics.scenery.volumes.BufferedVolume
import graphics.scenery.volumes.Colormap
import graphics.scenery.volumes.TransferFunction
import org.joml.Quaternionf
import org.joml.Vector3f

class BenchmarkSetup (var dataset: Dataset) {

    protected val logger by lazyLogger()

    enum class Dataset {
        Kingsnake,
        Rayleigh_Taylor,
        Richtmyer_Meshkov
    }

    fun getVolumeDims(): Vector3f {
        if(dataset == Dataset.Kingsnake) {
            return Vector3f(1024f, 1024f, 795f)
        } else if(dataset == Dataset.Rayleigh_Taylor) {
            return Vector3f(1024f, 1024f, 1024f)
        } else if (dataset == Dataset.Richtmyer_Meshkov) {
            return Vector3f(2048f, 2048f, 1920f)
        } else {
            return Vector3f(0f)
        }
    }

    fun positionCamera(cam: Camera) {
        with(cam) {
            spatial {
                if(dataset == Dataset.Kingsnake) {
                    position = Vector3f( 4.622E+0f, -9.060E-1f, -1.047E+0f)
                    rotation = Quaternionf( 5.288E-2, -9.096E-1, -1.222E-1,  3.936E-1)
                } else if (dataset == Dataset.Richtmyer_Meshkov) {
                    position = Vector3f(2.041E-1f, -5.253E+0f, -1.321E+0f)
                    rotation = Quaternionf(9.134E-2, -9.009E-1,  3.558E-1, -2.313E-1)
                } else if (dataset == Dataset.Rayleigh_Taylor) {
                    position = Vector3f( -2.300E+0f, -6.402E+0f,  1.100E+0f)
                    rotation = Quaternionf(2.495E-1, -7.098E-1,  3.027E-1, -5.851E-1)
                }
            }
        }
    }

    fun setupTransferFunction() : TransferFunction {
        val tf = TransferFunction()
        with(tf) {
            if (dataset == Dataset.Kingsnake) {
                addControlPoint(0.0f, 0.0f)
                addControlPoint(0.43f, 0.0f)
                addControlPoint(0.5f, 0.5f)
            } else if (dataset == Dataset.Richtmyer_Meshkov) {
                addControlPoint(0.0f, 0f)
                addControlPoint(0.1f, 0.0f)
                addControlPoint(0.15f, 0.65f)
                addControlPoint(0.22f, 0.15f)
                addControlPoint(0.28f, 0.0f)
                addControlPoint(0.49f, 0.0f)
                addControlPoint(0.7f, 0.95f)
                addControlPoint(0.75f, 0.8f)
                addControlPoint(0.8f, 0.0f)
                addControlPoint(0.9f, 0.0f)
                addControlPoint(1.0f, 0.0f)
            } else if (dataset == Dataset.Rayleigh_Taylor) {
                addControlPoint(0.0f, 0.95f)
                addControlPoint(0.15f, 0.0f)
                addControlPoint(0.45f, 0.0f)
                addControlPoint(0.5f, 0.35f)
                addControlPoint(0.55f, 0.0f)
                addControlPoint(0.80f, 0.0f)
                addControlPoint(1.0f, 0.378f)
            } else {
                logger.info("Using a standard transfer function")
                addControlPoint(0.0f, 0.0f)
                addControlPoint(1.0f, 1.0f)
            }
        }
        return tf
    }

    fun setColorMap(volume: BufferedVolume) {
        volume.name = "volume"
        volume.colormap = Colormap.get("hot")
        if(dataset == Dataset.Richtmyer_Meshkov) {
            volume.colormap = Colormap.get("rb")
            volume.converterSetups[0].setDisplayRange(50.0, 205.0)
        } else if(dataset == Dataset.Rayleigh_Taylor) {
            volume.colormap = Colormap.get("rbdarker")
        }
    }

}
