@file:Suppress("unused")

package graphics.scenery.utils

import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Maps the function [f] asynchronously on [this], returning the resultant list.
 *
 * Works via Kotlin Coroutines.
 */
fun <A, B>Iterable<A>.mapAsync(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(Dispatchers.Default) { f(it) } }.map { it.await() }
}

/**
 * Runs the function [f] asynchronously on [this].
 *
 * Works via Kotlin Coroutines.
 */
fun <A, B>Iterable<A>.forEachAsync(f: suspend (A) -> B) = runBlocking {
    map { async(Dispatchers.Default) { f(it) } }.forEach { it.await() }
}

/**
 * Runs the function [f] indexed asynchronously on [this].
 *
 * Works via Kotlin Coroutines.
 */
fun <A, B>Iterable<A>.forEachIndexedAsync(f: suspend (Int, A) -> B) = runBlocking {
    val index = AtomicInteger(0)
    map { async(Dispatchers.Default) { f(index.getAndIncrement(), it) } }.forEach { it.await() }
}

/**
 * Maps the function [transform] asynchronously on [this], returning the resultant list.
 * An optional [ExecutorService] may be given via [exec], the number of threads can be limited
 * by setting [numThreads], default number is (availableCores - 2).
 *
 * Works via Executor Services.
 * by Holger Brandl, https://stackoverflow.com/a/35638609
 */
fun <T, R> Iterable<T>.mapParallel(
    numThreads: Int = Runtime.getRuntime().availableProcessors() - 2,
    exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
    transform: (T) -> R): List<R> {

    // default size is just an inlined version of kotlin.collections.collectionSizeOrDefault
    val defaultSize = if (this is Collection<*>) this.size else 10
    val destination = Collections.synchronizedList(ArrayList<R>(defaultSize))

    for (item in this) {
        exec.submit { destination.add(transform(item)) }
    }

    exec.shutdown()
    exec.awaitTermination(1, TimeUnit.DAYS)

    return ArrayList<R>(destination)
}

/**
 * Runs the function [transform] asynchronously on [this], returning the resultant list.
 * An optional [ExecutorService] may be given via [exec], the number of threads can be limited
 * by setting [numThreads], default number is (availableCores - 2).
 *
 * Works via Executor Services.
 * derived from Holger Brandl's solution for map, https://stackoverflow.com/a/35638609
 */
fun <T, R> Iterable<T>.forEachParallel(
    numThreads: Int = Runtime.getRuntime().availableProcessors() - 2,
    exec: ExecutorService = Executors.newFixedThreadPool(numThreads),
    transform: (T) -> R) {

    // default size is just an inlined version of kotlin.collections.collectionSizeOrDefault
    val defaultSize = if (this is Collection<*>) this.size else 10
    val destination = Collections.synchronizedList(ArrayList<R>(defaultSize))

    for (item in this) {
        exec.submit { destination.add(transform(item)) }
    }

    exec.shutdown()
    exec.awaitTermination(1, TimeUnit.DAYS)
}

/**
 * Parallel forEach implementation for HashMaps.
 *
 * @param[maxThreads] Maximum number of parallel threads
 * @param[action] Lambda containing the action to be executed for each key, value pair.
 */
fun <K, V> HashMap<K, V>.forEachParallel(maxThreads: Int = 5, action: ((K, V) -> Unit)) {
    val iterator = this.asSequence().iterator()
    var threadCount = 0

    while (iterator.hasNext()) {
        val current = iterator.next()

        thread {
            threadCount++
            while (threadCount > maxThreads) {
                Thread.sleep(50)
            }

            action.invoke(current.key, current.value)
            threadCount--
        }
    }
}

/**
 * Launches a [Job] given by [action] that will be executed periodically, with a minimum delay
 * of [every] between individual launches. [every] can be an arbitrary [Duration] bigger than 0ns.
 */
fun CoroutineScope.launchPeriodicAsync(
    every: Duration,
    action: () -> Boolean
) = this.async {
    if (every > 0.nanoseconds) {
        while (isActive) {
            val result = action()
            if(result) {
                break
            }
            delay(every)
        }
    } else {
        action()
    }
}

