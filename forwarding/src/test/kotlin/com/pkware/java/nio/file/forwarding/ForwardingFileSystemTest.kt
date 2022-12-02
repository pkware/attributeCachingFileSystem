package com.pkware.java.nio.file.forwarding

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.FileSystem
import java.nio.file.Paths

class ForwardingFileSystemTest {

    @Test
    fun getPathVarargsAreCorrectlyForwarded() {
        val mock = mock(FileSystem::class.java)
        val stub = StubFileSystem(mock)

        // Varargs with no entries
        var path = Paths.get("first")
        `when`(mock.getPath("first")).thenReturn(path)
        assertThat(stub.getPath("first")).isSameInstanceAs(path)

        // Varargs with a single entry
        path = Paths.get("first", "second")
        `when`(mock.getPath("first", "second")).thenReturn(path)
        assertThat(stub.getPath("first", "second")).isSameInstanceAs(path)

        // Varargs with a multiple entries
        path = Paths.get("first", "second", "third")
        `when`(mock.getPath("first", "second", "third")).thenReturn(path)
        assertThat(stub.getPath("first", "second", "third")).isSameInstanceAs(path)
    }
}
