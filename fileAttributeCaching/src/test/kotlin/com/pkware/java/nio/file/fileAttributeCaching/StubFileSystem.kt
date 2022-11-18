package com.pkware.java.nio.file.fileAttributeCaching

import java.nio.file.FileSystem

/**
 * TODO figure out if needed.
 * Does nothing except forward calls to the [delegate]. Will not compile if [FileAttributeCachingFileSystem] does not
 * implement all members.
 */
class StubFileSystem(delegate: FileSystem) : FileAttributeCachingFileSystem(delegate)
