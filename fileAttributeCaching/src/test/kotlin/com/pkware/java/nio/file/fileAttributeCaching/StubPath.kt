package com.pkware.java.nio.file.fileAttributeCaching

import java.nio.file.Path

/**
 * Does nothing except forward calls to the [delegate]. Will not compile if [FileAttributeCachingPath] does not implement
 * all members.
 */
class StubPath(delegate: Path) : FileAttributeCachingPath(delegate)
