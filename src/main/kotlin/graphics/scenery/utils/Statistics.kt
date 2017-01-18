package graphics.scenery.utils

import graphics.scenery.Hub
import graphics.scenery.Hubable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Created by ulrik on 1/18/2017.
 */
class Statistics(override var hub: Hub?) : Hubable {
    private val dataSize = 100

    inner class StatisticData {
        var data: Deque<Float> = ConcurrentLinkedDeque()

        fun avg(): Float = data.sum() / data.size

        fun min(): Float = data.min() ?: 0.0f

        fun max(): Float = data.max() ?: 0.0f

        fun stddev(): Float = if (data.size == 0) {
            0.0f
        } else {
            Math.sqrt((data.map { (it - avg()) * (it - avg()) }.sum() / data.size).toDouble()).toFloat()
        }

        fun Float.inMilliseconds(): String {
            return String.format(Locale.US, "%.2f", this / 10e5)
        }

        override fun toString(): String = "${avg().inMilliseconds()}\tmin: ${min().inMilliseconds()}\tmax: ${max().inMilliseconds()}\tstd: ${stddev().inMilliseconds()}"
    }

    protected var stats = ConcurrentHashMap<String, StatisticData>()

    fun add(name: String, time: Float) {
        if (stats.containsKey(name)) {
            if (stats.get(name)!!.data.size >= dataSize) {
                stats.get(name)!!.data.pop()
            }

            stats.get(name)!!.data.add(time)
        } else {
            val d = StatisticData()
            d.data.add(time)

            stats.put(name, d)
        }
    }

    fun addTimed(name: String, lambda: () -> Any) {
        val start = System.nanoTime()
        lambda.invoke()
        val duration = System.nanoTime() - start

        add(name, duration * 1.0f)
    }

    fun get(name: String) = stats.get(name)

    override fun toString(): String {
        return "Statistics:\n" + stats.map {
            "${it.key} - ${it.value}"
        }.joinToString("\n")
    }

    fun toString(name: String): String {
        if (stats.containsKey(name)) {
            return "$name - ${stats.get(name)!!}"
        } else {
            return ""
        }
    }
}
