package graphics.scenery.utils

import graphics.scenery.Hub
import graphics.scenery.Hubable
import kotlinx.coroutines.experimental.async
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Created by ulrik on 1/18/2017.
 */
class Statistics(override var hub: Hub?) : Hubable {
    private val logger by LazyLogger()
    private val dataSize = 100

    inner class StatisticData(val isTime: Boolean) {
        var data: Deque<Float> = ConcurrentLinkedDeque()

        fun avg(): Float = data.sum() / data.size

        fun min(): Float = data.min() ?: 0.0f

        fun max(): Float = data.max() ?: 0.0f

        fun stddev(): Float = if (data.size == 0) {
            0.0f
        } else {
            Math.sqrt((data.map { (it - avg()) * (it - avg()) }.sum() / data.size).toDouble()).toFloat()
        }

        fun Float.inMillisecondsIfTime(): String {
            if(isTime) {
                return String.format(Locale.US, "%.2f", this / 1000000)
            } else {
                return String.format(Locale.US, "%.2f", this)
            }
        }

        override fun toString(): String = "${avg().inMillisecondsIfTime()}\tmin: ${min().inMillisecondsIfTime()}\tmax: ${max().inMillisecondsIfTime()}\tstd: ${stddev().inMillisecondsIfTime()}"
    }

    protected var stats = ConcurrentHashMap<String, StatisticData>()

    fun add(name: String, value: Float, isTime: Boolean = true) {
        async {
            if (stats.containsKey(name)) {
                if (stats.get(name)!!.data.size >= dataSize) {
                    stats.get(name)!!.data.removeLast()
                }

                stats.get(name)!!.data.push(value)
            } else {
                val d = StatisticData(isTime)
                d.data.push(value)

                stats.put(name, d)
            }
        }
    }

    fun add(name: String, value: Number, isTime: Boolean = true) {
        add(name, value.toFloat(), isTime)
    }

    inline fun addTimed(name: String, lambda: () -> Any) {
        val start = System.nanoTime()
        lambda.invoke()
        val duration = System.nanoTime() - start

        add(name, duration * 1.0f)
    }

    fun get(name: String) = stats.get(name)

    override fun toString(): String {
        return "Statistics:\n" + stats.toSortedMap().map {
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

    fun log() {
         logger.info(this.toString())
    }
}
