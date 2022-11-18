package com.pkware.java.nio.file.fileAttributeCaching

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.spi.FileSystemProvider

class FileAttributeCachingFilesystemProviderTest {
    private lateinit var mockProvider: FileSystemProvider
    private lateinit var stubProvider: FileSystemProvider
    private lateinit var stubPath: Path

    @BeforeEach
    fun setUp() {
        mockProvider = Mockito.mock(FileSystemProvider::class.java)
        stubProvider = StubFileSystemProvider(mockProvider)
        stubPath = StubPath(Paths.get("first"))
    }

    @Test
    fun `read attributes by class type from provider`() {
        return
    }

    @Test
    fun `read all attributes for one name from provider`() {
        return
    }

    @Test
    fun `read single attribute for one name from provider`() {
        return
    }

    @Test
    fun `set single attribute for one name from provider`() {
        return
    }

    @Test
    fun `init all attributes for given path from provider`() {
        return
    }
}
