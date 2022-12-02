package com.pkware.java.nio.file.fileAttributeCaching

import com.pkware.java.nio.file.forwarding.ForwardingPath
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
 * @param delegate the [Path] to forward calls to if needed.
 */
class FileAttributeCachingPath(
    delegate: Path
) : ForwardingPath(delegate) {

    // ExpirableCache for thisFileAttributeCachingPath a BasicFileAttributes cache
    private val attributeCache = ExpirableCache<BasicFileAttributes?>(
        TimeUnit.SECONDS.toMillis(CACHE_PRESERVATION_SECONDS)
    )

    /**
     * Sets the cache entry for the given attribute [name] with the given [value]. Can set single attributes or entire
     * attribute `Class`es such as "dos", "posix", and "basic"
     *
     * @param name The name of the attribute to cache.
     * @param value The attribute value to cache.
     */
    fun <A : BasicFileAttributes?> setAttributeByName(name: String, value: A?) {
        // remove basic from our attribute name if present as basicFileAttributes can be accessed without that qualifier
        val checkedName = name.substringAfter("basic:")

        attributeCache[checkedName] = value
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
            BasicFileAttributes::class -> attributeCache["*"] = value
            DosFileAttributes::class -> attributeCache["dos:*"] = value
            PosixFileAttributes::class -> attributeCache["posix:*"] = value
        }
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
        BasicFileAttributes::class -> attributeCache.computeIfExpiredOrAbsent("*", mappingFunction) as A
        DosFileAttributes::class -> attributeCache.computeIfExpiredOrAbsent("dos:*", mappingFunction) as A
        PosixFileAttributes::class -> attributeCache.computeIfExpiredOrAbsent("posix:*", mappingFunction) as A
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
}
