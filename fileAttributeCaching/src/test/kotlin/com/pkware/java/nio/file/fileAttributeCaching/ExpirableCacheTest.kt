package com.pkware.java.nio.file.fileAttributeCaching

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ExpirableCacheTest {

    private val attributeCache = ExpirableCache<String>(
        TimeUnit.SECONDS.toMillis(3)
    )

    @BeforeEach
    fun setUp() {
        attributeCache["test"] = "happy"
        attributeCache["test2"] = "happy2"
    }

    @Test
    fun cacheExpiresProperly() {
        attributeCache["test3"] = "deadbeef"

        Thread.sleep(4000)

        assertThat(attributeCache.size).isEqualTo(3)
        assertThat(attributeCache["test"]).isEqualTo(null)
        assertThat(attributeCache.size).isEqualTo(0)
    }

    @Test
    fun cacheDoesntExpireProperly() {
        attributeCache["test3"] = "deadbeef"

        Thread.sleep(1000)

        assertThat(attributeCache.size).isEqualTo(3)
        assertThat(attributeCache["test"]).isEqualTo("happy")
        assertThat(attributeCache["test2"]).isEqualTo("happy2")
        assertThat(attributeCache["test3"]).isEqualTo("deadbeef")
    }

    @Test
    fun `computeIfAbsent has cache absence`() {
        attributeCache.computeIfExpiredOrAbsent("test3") { "huge" }

        assertThat(attributeCache.size).isEqualTo(3)
        assertThat(attributeCache["test"]).isEqualTo("happy")
        assertThat(attributeCache["test2"]).isEqualTo("happy2")
        assertThat(attributeCache["test3"]).isEqualTo("huge")
    }

    @Test
    fun `computeIfAbsent does not have cache absence`() {
        attributeCache["test3"] = "deadbeef"

        attributeCache.computeIfExpiredOrAbsent("test3") { "huge" }

        assertThat(attributeCache.size).isEqualTo(3)
        assertThat(attributeCache["test"]).isEqualTo("happy")
        assertThat(attributeCache["test2"]).isEqualTo("happy2")
        assertThat(attributeCache["test3"]).isEqualTo("deadbeef")
    }

    @Test
    fun `computeIfExpired cache has expired`() {
        attributeCache["test3"] = "deadbeef"

        Thread.sleep(4000)

        assertThat(attributeCache.size).isEqualTo(3)

        attributeCache.computeIfExpiredOrAbsent("test3") { "huge" }

        assertThat(attributeCache.size).isEqualTo(1)
        assertThat(attributeCache["test3"]).isEqualTo("huge")
    }

    @Test
    fun `cache can expire multiple times`() {
        attributeCache["test3"] = "deadbeef"

        Thread.sleep(4000)

        assertThat(attributeCache.size).isEqualTo(3)

        attributeCache.computeIfExpiredOrAbsent("test3") { "huge" }

        assertThat(attributeCache.size).isEqualTo(1)
        assertThat(attributeCache["test3"]).isEqualTo("huge")

        Thread.sleep(4000)

        assertThat(attributeCache.size).isEqualTo(1)
        assertThat(attributeCache["test3"]).isEqualTo(null)
        assertThat(attributeCache.size).isEqualTo(0)
    }
}
