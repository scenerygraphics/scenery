package graphics.scenery.volumes.bdv

import bdv.tools.brightness.ConverterSetup
import tpietzsch.multires.Stack3D

interface VolumeBacking {
    fun getStack(timepoint: Int, setup: Int): Stack3D<*>
    fun getConverter(setup: Int): ConverterSetup
    fun getCurrentTimepoint(): Int
    fun getVisibleSourceIndices(): List<Int>
}
