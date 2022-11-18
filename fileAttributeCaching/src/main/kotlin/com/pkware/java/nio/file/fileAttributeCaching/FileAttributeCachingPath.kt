package com.pkware.java.nio.file.fileAttributeCaching

import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.util.concurrent.TimeUnit
import java.util.function.Function

private const val CACHE_PRESERVATION_SECONDS: Long = 5

/**
 * TODO document.
 * TODO need to figure out how to have this class as one that only overrides the changed functions rather than everything for Path.
 */
abstract class FileAttributeCachingPath(
    /**
     * TODO document.
     */
    val delegate: Path

) : Path {

    // ExpirableCache is a BasicFileAttributes cache
    private val attributeCache = ExpirableCache<BasicFileAttributes?>(
        TimeUnit.SECONDS.toMillis(CACHE_PRESERVATION_SECONDS)
    )

    /**
     * TODO document.
     */
    fun <A : BasicFileAttributes?> setAttributeByName(name: String, value: A?) {
        // remove basic from our attribute name if present as basicFileAttributes can be accessed without that qualifier
        val checkedName = name.substringAfter("basic:")

        attributeCache[checkedName] = value
    }

    /**
     * TODO document.
     */
    fun <A : BasicFileAttributes?> setAttributeByType(type: Class<A>, value: A?) {
        when (type) {
            BasicFileAttributes::class -> attributeCache["*"] = value
            DosFileAttributes::class -> attributeCache["dos:*"] = value
            PosixFileAttributes::class -> attributeCache["posix:*"] = value
        }
    }

    /**
     * TODO document.
     */
    @Suppress("UNCHECKED_CAST")
    fun <A : BasicFileAttributes?> getAllAttributesMatchingClass(
        type: Class<A>,
        mappingFunction: Function<in String?, out A?>
    ): A? = when (type) {
        BasicFileAttributes::class -> attributeCache.computeIfExpiredOrAbsent("*", mappingFunction) as A
        DosFileAttributes::class -> attributeCache.computeIfExpiredOrAbsent("dos:*", mappingFunction) as A
        PosixFileAttributes::class -> attributeCache.computeIfExpiredOrAbsent("posix:*", mappingFunction) as A
        else -> null
    }

    /**
     * TODO document.
     */
    fun <A : BasicFileAttributes?> getAllAttributesMatchingName(
        name: String,
        mappingFunction: Function<in String?, out A?>
    ): MutableMap<String, Any>? {

        var attributeMap = mutableMapOf<String, Any>()
        // remove basic: from our attribute name if present as basicFileAttributes can be accessed without that qualifier
        val checkedName = name.substringAfter("basic:")

        // get our attribute class from the cache, should be BasicFileAttributes, DosFileAttributes, or PosixFileAttributes
        val attributeClass = if (checkedName.startsWith("dos")) {
            attributeCache.computeIfExpiredOrAbsent("dos:*", mappingFunction)
        } else if (checkedName.startsWith("posix")) {
            attributeCache.computeIfExpiredOrAbsent("posix:*", mappingFunction)
        } else {
            attributeCache.computeIfExpiredOrAbsent("*", mappingFunction)
        }

        if (attributeClass == null) return null

        // translate our class object to MutableMap<String, Any>?

        // get basic file attributes
        // these should always exist because DosFileAttributes and PosixFileAttributes extend BasicFileAttributes
        attributeMap["lastModifiedTime"] = attributeClass.lastModifiedTime()
        attributeMap["lastAccessTime"] = attributeClass.lastAccessTime()
        attributeMap["creationTime"] = attributeClass.creationTime()
        // TODO not sure if key name is correct for any BasicFileAttributes below this line, hard to find docs
        attributeMap["regularFile"] = attributeClass.isRegularFile
        attributeMap["directory"] = attributeClass.isDirectory
        attributeMap["symbolicLink"] = attributeClass.isSymbolicLink
        attributeMap["other"] = attributeClass.isOther
        attributeMap["size"] = attributeClass.size()
        attributeMap["fileKey"] = attributeClass.fileKey()

        // attributeClass may or may not be either of the following BasicFileAttributes subclasses
        if (attributeClass is DosFileAttributes) {
            attributeMap["dos:readonly"] = attributeClass.isReadOnly
            attributeMap["dos:hidden"] = attributeClass.isHidden
            attributeMap["dos:archive"] = attributeClass.isArchive
            attributeMap["dos:system"] = attributeClass.isSystem
        } else if (attributeClass is PosixFileAttributes) {
            attributeMap["posix:owner"] = attributeClass.owner()
            attributeMap["posix:group"] = attributeClass.group()
            attributeMap["posix:permissions"] = attributeClass.permissions()
        }

        // filter out attributes for a specific checkedName if the checkedName does not contain the "*" wildcard
        if (!checkedName.contains("*")) {
            attributeMap = attributeMap.filter { it.key == checkedName }.toMutableMap()
        }

        if (attributeMap.isEmpty()) return null

        return attributeMap
    }

    override fun endsWith(other: String): Boolean = delegate.endsWith(other)

    override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>?): WatchKey =
        delegate.register(watcher, events)

    override fun startsWith(other: String): Boolean = delegate.startsWith(other)

    override fun resolve(other: String): Path = delegate.resolve(other)

    override fun toFile(): File = delegate.toFile()

    override fun iterator(): MutableIterator<Path> = delegate.iterator()

    override fun resolveSibling(other: Path): Path = delegate.resolveSibling(other)

    override fun resolveSibling(other: String): Path = delegate.resolveSibling(other)

    override fun getFileSystem(): FileSystem = delegate.fileSystem

    override fun getFileName(): Path = delegate.fileName

    override fun isAbsolute(): Boolean = delegate.isAbsolute

    override fun getName(index: Int): Path = delegate.getName(index)

    override fun subpath(beginIndex: Int, endIndex: Int): Path = delegate.subpath(beginIndex, endIndex)

    override fun endsWith(other: Path): Boolean = delegate.endsWith(other)

    override fun register(
        watcher: WatchService,
        events: Array<out WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier?
    ): WatchKey = delegate.register(watcher, events, *modifiers)

    override fun relativize(other: Path): Path = delegate.relativize(other)

    override fun toUri(): URI = delegate.toUri()

    override fun toRealPath(vararg options: LinkOption?): Path = delegate.toRealPath(*options)

    override fun normalize(): Path = delegate.normalize()

    override fun getParent(): Path = delegate.parent

    override fun compareTo(other: Path): Int = delegate.compareTo(other)

    override fun getNameCount(): Int = delegate.nameCount

    override fun startsWith(other: Path): Boolean = delegate.startsWith(other)

    override fun getRoot(): Path = delegate.root

    override fun resolve(other: Path): Path = delegate.resolve(other)

    override fun toAbsolutePath(): Path = delegate.toAbsolutePath()
}
