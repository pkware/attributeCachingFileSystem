package com.pkware.java.nio.file.fileAttributeCaching

import java.time.Clock
import java.util.function.Function

/**
 * TODO document.
 */
class ExpirableCache <A> (private val flushInterval: Long) {
    private val clock = Clock.systemUTC()
    private var lastFlushTime = clock.millis()
    private val cache = HashMap<String, A?>()

    /**
     * TODO document.
     */
    val size: Int
        get() = cache.size

    /**
     * TODO document.
     */
    fun computeIfExpiredOrAbsent(
        key: String,
        mappingFunction: Function<in String?, out A?>
    ): A? {
        recycle()
        return cache.computeIfAbsent(key, mappingFunction)
    }

    /**
     * TODO document.
     */
    operator fun set(key: String, value: A?) {
        cache[key] = value
    }

    /**
     * TODO document.
     */
    fun remove(key: String): A? {
        recycle()
        return cache.remove(key)
    }

    /**
     * TODO document.
     */
    operator fun get(key: String): A? {
        recycle()
        return cache[key]
    }

    /**
     * TODO document.
     */
    fun clear() = cache.clear()

    /**
     * TODO document.
     */
    private fun recycle() {
        val currentMillis = clock.millis()
        val shouldRecycle = currentMillis - lastFlushTime >= flushInterval
        if (!shouldRecycle) return
        lastFlushTime = currentMillis
        cache.clear()
    }
}
