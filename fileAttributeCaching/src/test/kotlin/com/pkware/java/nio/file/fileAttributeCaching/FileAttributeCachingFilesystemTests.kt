package com.pkware.java.nio.file.fileAttributeCaching

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
// import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
// import org.junit.jupiter.api.io.TempDir
// import org.junit.jupiter.params.ParameterizedTest
// import org.junit.jupiter.params.provider.MethodSource
// import org.mockito.Mockito
// import java.nio.file.FileSystem
// import java.nio.file.FileSystems
import java.nio.file.Files
// import java.nio.file.Path
// import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
// import java.nio.file.spi.FileSystemProvider
// import kotlin.io.path.div

class FileAttributeCachingFilesystemTests {
    /*private lateinit var cachingFileSystemProvider: FileAttributeCachingFileSystemProvider

    @BeforeEach
    fun setUp() {
        /*cachingFileSystemProvider = FileAttributeCachingFileSystemProvider(
            Mockito.mock(FileSystemProvider::class.java)
        )*/
        //TODO does not work, alternate possible method
        //cachingFileSystemProvider = FileAttributeCachingFileSystemProvider(FileSystems.getDefault().provider())
    }*/

    @Test
    fun `read attributes by class type from provider`() = Jimfs.newFileSystem().use {
        // get file attribute caching path
        val cachingPath = FileAttributeCachingPath(it.getPath(""))
        // read dos file attributes for path from provider
        val attributes = Files.readAttributes(cachingPath, BasicFileAttributes::class.java)
        // val attributes = provider.readAttributes(cachingPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        // TODO Verify that attribute is "right" based on FS
        assertThat(attributes).isInstanceOf(BasicFileAttributes::class.java)
    }
    // TODO does not work, alternate possible method
    /*fun `read attributes by class type from provider`(@TempDir tempDir: Path) {
        val path = tempDir / "tmp.txt"

        Files.deleteIfExists(path)
        Files.createFile(path)

        val cachingPath = cachingFileSystemProvider.getPath(path.toUri())

        // get file attribute caching path
        // read dos file attributes for path from provider
        val attributes = Files.readAttributes(cachingPath, BasicFileAttributes::class.java)
        // val attributes = provider.readAttributes(cachingPath, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        // TODO Verify that attribute is "right" based on FS
        assertThat(attributes).isInstanceOf(BasicFileAttributes::class.java)
    }*/

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
