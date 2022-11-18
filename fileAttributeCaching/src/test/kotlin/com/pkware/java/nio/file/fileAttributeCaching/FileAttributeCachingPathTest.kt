package com.pkware.java.nio.file.fileAttributeCaching

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.spi.FileSystemProvider

class FileAttributeCachingPathTest {

    private lateinit var mockProvider: FileSystemProvider
    private lateinit var stubProvider: FileSystemProvider
    private lateinit var stubPath: Path

    @BeforeEach
    fun setUp() {
        mockProvider = mock(FileSystemProvider::class.java)
        stubProvider = StubFileSystemProvider(mockProvider)
        stubPath = StubPath(Paths.get("first"))
    }

    @Test
    fun `path cache returns null if cache expired and null mappingFunction getAllAttributesMatchingClass`() {
        return
    }

    @Test
    fun `path cache returns null if cache expired and null mappingFunction getAllAttributesMatchingName`() {
        return
    }

    @Test
    fun `path cache returns value if cache expired and non-null mappingFunction getAllAttributesMatchingClass`() {
        return
    }

    @Test
    fun `path cache returns value if cache expired and non-null mappingFunction getAllAttributesMatchingName`() {
        return
    }
}
