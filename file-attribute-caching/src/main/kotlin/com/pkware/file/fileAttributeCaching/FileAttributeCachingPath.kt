package com.pkware.file.fileAttributeCaching

import com.pkware.file.forwarding.ForwardingPath
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.util.concurrent.TimeUnit
import java.util.function.Function

private const val CACHE_PRESERVATION_SECONDS: Long = 5

/**
 * A [Path] instance that supports caching of [BasicFileAttributes] and other classes that extend it such as
 * [DosFileAttributes] and [PosixFileAttributes].
 *
 * The cache duration is preset to be [CACHE_PRESERVATION_SECONDS].
 *
 * @param fileSystem the [FileSystem] associated with this [FileAttributeCachingPath] instance.
 * @param delegate the [Path] to forward calls to if needed.
 */
internal class FileAttributeCachingPath(
    private val fileSystem: FileSystem,
    internal val delegate: Path
) : ForwardingPath(delegate) {

    // ExpirableCache for thisFileAttributeCachingPath a BasicFileAttributes cache
    private val attributeCache = ExpirableCache<String, BasicFileAttributes?>(
        TimeUnit.SECONDS.toMillis(CACHE_PRESERVATION_SECONDS)
    )

    override fun getFileSystem(): FileSystem = fileSystem

    /**
     * Sets the cache entry for the given attribute [name] with the given [value]. Can only set entire
     * attribute `Class`es such as "dos:*", "posix:*", and "basic:*"
     *
     * The attribute name must include a "*" in order to be set within the cache.
     *
     * @param name The name of the attribute to cache.
     * @param value The attribute value to cache.
     */
    fun <A : BasicFileAttributes?> setAttributeByName(name: String, value: A?) {
        // remove basic from our attribute name if present as basicFileAttributes can be accessed without that qualifier
        val checkedName = name.substringAfter("basic:")

        // this check is to ensure that we are only storing attribute classes and not specific attributes
        if (checkedName.contains("*")) {
            attributeCache[checkedName] = value
        }
    }

    /**
     * Sets the cache entry for the given attribute `Class` [type] with the given [value].
     *
     * @param type The attribute `Class` to cache. `Class` types include [BasicFileAttributes], [DosFileAttributes],
     * or [PosixFileAttributes].
     * @param value The attribute value to cache.
     */
    fun <A : BasicFileAttributes?> setAttributeByType(type: Class<A>, value: A?) {
        when (type) {
            BasicFileAttributes::class.java -> attributeCache["*"] = value
            DosFileAttributes::class.java -> attributeCache["dos:*"] = value
            PosixFileAttributes::class.java -> attributeCache["posix:*"] = value
        }
    }

    /**
     * Copies this [FileAttributeCachingPath]s cached values to the [target]. Also runs [functionToExecute] midway
     * through function execution to provide support to external operations such as
     * [FileAttributeCachingFileSystemProvider.copy] and [FileAttributeCachingFileSystemProvider.move].
     *
     * @param target The [FileAttributeCachingPath] to copy cached attributes to.
     * @param functionToExecute The [Runnable] function to execute midway through this function.
     */
    fun copyCachedAttributesTo(target: FileAttributeCachingPath, functionToExecute: Runnable) {
        val delegateFileSystem = delegate.fileSystem
        val delegateProvider = delegateFileSystem.provider()
        val supportedViews = delegateFileSystem.supportedFileAttributeViews()

        // getAllAttributesMatchingClass takes care of cache expiration and computation if this source cache is null
        // or expired
        val basicFileAttributes = getAllAttributesMatchingClass(BasicFileAttributes::class.java) {
            delegateProvider.readAttributes(delegate, BasicFileAttributes::class.java)
        }

        val dosFileAttributes = if (supportedViews.contains("dos")) {
            getAllAttributesMatchingClass(DosFileAttributes::class.java) {
                delegateProvider.readAttributes(delegate, DosFileAttributes::class.java)
            }
        } else null

        val posixFileAttributes = if (supportedViews.contains("posix")) {
            getAllAttributesMatchingClass(PosixFileAttributes::class.java) {
                delegateProvider.readAttributes(delegate, PosixFileAttributes::class.java)
            }
        } else null

        functionToExecute.run()

        // Can set null values here but that's okay, next time value is read as null it will be computed
        // from outside the cache.
        target.setAttributeByType(BasicFileAttributes::class.java, basicFileAttributes)
        target.setAttributeByType(DosFileAttributes::class.java, dosFileAttributes)
        target.setAttributeByType(PosixFileAttributes::class.java, posixFileAttributes)
    }

    /**
     * Get all attributes matching the `Class` [type] from the cache.
     *
     * If the given [type] is absent or the cache is expired, the [mappingFunction] is used to compute the `Class` data
     * and populate the cache as well as return it.
     *
     * @param type The attribute `Class` to get from the cache. Class` types include [BasicFileAttributes],
     * [DosFileAttributes], or [PosixFileAttributes].
     * @param mappingFunction The function to use when computing a new cache value if the given [type] is not found or
     * the cache is expired. This must not be `null`.
     * @return The value in the cache that corresponds to the given [type] or `null` if that [type] is not
     * supported or the [mappingFunction] could not compute it.
     */
    @Suppress("UNCHECKED_CAST")
    fun <A : BasicFileAttributes?> getAllAttributesMatchingClass(
        type: Class<A>,
        mappingFunction: Function<in String?, out A?>
    ): A? = when (type) {
        BasicFileAttributes::class.java -> attributeCache.computeIfExpiredOrAbsent("*", mappingFunction) as A
        DosFileAttributes::class.java -> attributeCache.computeIfExpiredOrAbsent("dos:*", mappingFunction) as A
        PosixFileAttributes::class.java -> attributeCache.computeIfExpiredOrAbsent("posix:*", mappingFunction) as A
        else -> null
    }

    /**
     * Get all attributes matching [name] from the cache.
     *
     * If the given [name] is absent or the cache is expired, the [mappingFunction] is used to compute the returned map
     * data and populate the cache for future calls.
     *
     * @param name The attributes to be retrieved from the cache. Can be single attributes or an entire attribute
     * `Class` String (ie: "dos:*","basic:*","posix:permissions", etc.).
     * @param mappingFunction The function to use when computing a new cache value if the given [name] is not found or
     * the cache is expired. This must not be `null`.
     * @return The value in the cache that corresponds to the given [name] or `null` if that [name] is not
     * supported or the [mappingFunction] could not compute it.
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
}
