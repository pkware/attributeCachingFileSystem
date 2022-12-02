package com.pkware.java.nio.file.fileAttributeCaching

import com.pkware.java.nio.file.forwarding.ForwardingFileSystem
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider

public class FileAttributeCachingFileSystem(
    delegate: FileSystem,
    private val provider: FileSystemProvider
) : ForwardingFileSystem(delegate) {

    override fun provider(): FileSystemProvider = provider

    override fun getPath(first: String, vararg more: String?): Path {
        val delegate = super.getPath(first, *more)
        return FileAttributeCachingPath(this, delegate)
    }

    public companion object {
        public fun wrapping(fileSystem: FileSystem): FileSystem {
            return FileSystems.newFileSystem(URI.create("cache:///"), mapOf(Pair("filesystem", fileSystem)))
        }
    }
}
