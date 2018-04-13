package graphics.scenery.utils

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

fun <A, B>Iterable<A>.mapAsync(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(CommonPool) { f(it) } }.map { it.await() }
}

fun <A, B>Iterable<A>.forEachAsync(f: suspend (A) -> B) = runBlocking {
    map { async(CommonPool) { f(it) } }.forEach { it.await() }
}

fun <A, B>Iterable<A>.forEachIndexedAsync(f: suspend (Int, A) -> B) = runBlocking {
    val index = AtomicInteger(0)
    map { async(CommonPool) { f(index.getAndIncrement(), it) } }.forEach { it.await() }
}

// by Holger Brandl, https://stackoverflow.com/a/35638609
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

// derived from Holger Brandl's answer at https://stackoverflow.com/a/35638609
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
