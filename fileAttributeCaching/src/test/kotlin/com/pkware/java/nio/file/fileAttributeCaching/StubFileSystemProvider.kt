package com.pkware.java.nio.file.fileAttributeCaching

import java.nio.file.spi.FileSystemProvider

/**
 * Does nothing except forward calls to the [delegate]. Will not compile if [FileAttributeCachingFileSystem] does not
 * implement all members.
 */
class StubFileSystemProvider(delegate: FileSystemProvider) : FileAttributeCachingFileSystemProvider(delegate)
