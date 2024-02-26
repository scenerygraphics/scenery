package graphics.scenery.utils

import graphics.scenery.Hub
import graphics.scenery.Hubable
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


/**
 * Statistics class, attached to a [hub].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class Statistics(override var hub: Hub?) : Hubable {
    private val logger by lazyLogger()
    private val dataSize = 100
    private val threadContext = Executors.newSingleThreadExecutor()

    /**
     * Keeps statistical data, such as time points, and
     * provides min, max, averages, etc. Set [isTime] to true if the
     * kept data is time data.
     */
    inner class StatisticData(val isTime: Boolean) {
        /** Data storage */
        var data: Deque<Float> = ConcurrentLinkedDeque()

        /** Returns the average of the [data] */
        fun avg(): Float = data.sum() / data.size

        /** Returns the minimum of the [data] or 0.0f if empty */
        fun min(): Float = data.minOrNull() ?: 0.0f

        /** Returns the maximum of the [data] or 0.0f if empty */
        fun max(): Float = data.maxOrNull() ?: 0.0f

        /** Returns the standard deviation of the [data] or 0.0f if empty */
        fun stddev(): Float = if (data.size == 0) {
            0.0f
        } else {
            Math.sqrt((data.map { (it - avg()) * (it - avg()) }.sum() / data.size).toDouble()).toFloat()
        }

        /**
         * Returns a nanosecond float datum formatted converted to milliseconds with two digits
         * precision, if [isTime] is true.
         */
        fun Float.inMillisecondsIfTime(): String {
            return if(isTime) {
                String.format(Locale.US, "%.2f", this / 1000000)
            } else {
                String.format(Locale.US, "%.2f", this)
            }
        }

        /** Returns all the stats about [data] formatted as string */
        override fun toString(): String = "${avg().inMillisecondsIfTime()}/${min().inMillisecondsIfTime()}/${max().inMillisecondsIfTime()}/${stddev().inMillisecondsIfTime()}/${data.first.inMillisecondsIfTime()}"
    }

    protected var stats = ConcurrentHashMap<String, StatisticData>()

    /**
     * Adds a new datum to the statistic about [name] with [value].
     * Set [isTime] to true if the datum contains time information.
     */
    fun add(name: String, value: Float, isTime: Boolean = true) {
        GlobalScope.launch(threadContext.asCoroutineDispatcher()) {
            stats.computeIfAbsent(name) {
                val d = StatisticData(isTime)
                d.data.push(value)
                d
            }.let {
                if(it.data.size >= dataSize) {
                    it.data.removeLast()
                }

                it.data.push(value)
            }
        }
    }

    /**
     * Remove stat [name] from [stats]. Can be used, e.g., to reset
     * the stat.
     */
    fun clear(name: String) {
        stats.remove(name)
    }

    /**
     * Adds a new datum to the statistic about [name] with [value].
     * Accepts all types of numbers.
     *
     * Set [isTime] to true if the datum contains time information.
     */
    fun add(name: String, value: Number, isTime: Boolean = true) {
        add(name, value.toFloat(), isTime)
    }

    /**
     * Adds a new datum to the statistic about [name], with the value
     * determined as the duration of running [lambda].
     */
    inline fun addTimed(name: String, lambda: () -> Any) {
        val start = System.nanoTime()
        lambda.invoke()
        val duration = System.nanoTime() - start

        add(name, duration * 1.0f)
    }

    /** Returns the statistic of [name], or null if not found */
    fun get(name: String) = stats.get(name)

    /** Returns all collected stats as string */
    override fun toString(): String {
        val longestKey: Int = stats.keys().asSequence().map { it.length }.maxOrNull() ?: 1
        return "Statistics - avg/min/max/stddev/last\n" + stats.toSortedMap().map {
            String.format("%-${longestKey}s", it.key) + " - ${it.value}"
        }.joinToString("\n")
    }

    /** Returns the statistics for [name] as string */
    fun toString(name: String): String {
        val stat = stats[name] ?: return ""
        return "$name - $stat"
    }

    /** Logs all statistics as info via the logger infrastructure */
    @Suppress("unused") fun log() {
         logger.info(this.toString())
    }

    /** Closes the Statistics */
    fun close() {
        threadContext.shutdown()
        try {
            if (!threadContext.awaitTermination(2, TimeUnit.SECONDS)) {
                threadContext.shutdownNow()
                if (!threadContext.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.error("Pool did not terminate")
                }
            }
        } catch (ie: InterruptedException) {
            threadContext.shutdownNow()
            Thread.currentThread().interrupt()
        }

        logger.debug("Closed Statistics")
    }
}
