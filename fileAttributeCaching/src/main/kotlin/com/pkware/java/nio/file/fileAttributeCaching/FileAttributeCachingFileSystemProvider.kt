package com.pkware.java.nio.file.fileAttributeCaching

import java.io.IOException
import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessMode
import java.nio.file.CopyOption
import java.nio.file.DirectoryStream
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.spi.FileSystemProvider

/**
 * A [FileSystemProvider] wrapper that handles [FileAttributeCachingPath]s for reading and writing file attributes.
 *
 * It forwards all operations to the delegate filesystem except for reading and writing file attributes.
 * Those events are only forwarded if the values obtained from [FileAttributeCachingPath] are null.
 *
 * TODO need to figure out how to have this class as one that only overrides the changed functions rather than everything for FileSystemProvider.
 *
 * @param delegate The [FileSystemProvider] to forward calls to.
 */
abstract class FileAttributeCachingFileSystemProvider(private val delegate: FileSystemProvider) : FileSystemProvider() {
    override fun checkAccess(path: Path, vararg modes: AccessMode) = delegate.checkAccess(path, *modes)

    override fun copy(source: Path, target: Path, vararg options: CopyOption) = delegate.copy(source, target, *options)

    override fun <A : FileAttributeView?> getFileAttributeView(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A = delegate.getFileAttributeView(path, type, *options)

    override fun isSameFile(path: Path, path2: Path): Boolean = delegate.isSameFile(path, path2)

    override fun newFileSystem(uri: URI, env: MutableMap<String, *>): FileSystem = delegate.newFileSystem(uri, env)

    override fun getScheme(): String = delegate.scheme

    override fun isHidden(path: Path): Boolean = delegate.isHidden(path)

    override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>): DirectoryStream<Path> =
        delegate.newDirectoryStream(dir, filter)

    override fun newByteChannel(
        path: Path,
        options: MutableSet<out OpenOption>,
        vararg attrs: FileAttribute<*>
    ): SeekableByteChannel = delegate.newByteChannel(path, options, *attrs)

    override fun delete(path: Path) = delegate.delete(path)

    /**
     * Read file attributes specified by the attribute `Class` [type] from the incoming [path]. If the returned
     * attributes are null we then attempt to get them from the delegate [FileSystemProvider] and populate the [path]
     * with those attributes.
     *
     * The value returned will always be from the [path] parameter itself, never directly from the [delegate].
     *
     * @param path The [Path] to read file attributes from. It must be a [FileAttributeCachingPath] otherwise an
     * [IOException] will be thrown.
     * @param type The `Class` of the file attributes to be read. `Class` types include [BasicFileAttributes],
     * [DosFileAttributes], or [PosixFileAttributes].
     * @param options The [LinkOption]s indicating how symbolic links are handled.
     * @return The file attributes for the given [path].
     * @throws IOException If the [path] is not a [FileAttributeCachingPath] or if something goes wrong with the
     * underlying calls to the delegate [FileSystemProvider].
     * @throws UnsupportedOperationException If the attributes of the given [type] are not supported.
     */
    @Throws(IOException::class, UnsupportedOperationException::class)
    override fun <A : BasicFileAttributes?> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A = if (path is FileAttributeCachingPath) {
        val attributes = path.getAllAttributesMatchingClass(type) {
            delegate.readAttributes(path, type, *options)
        } ?: throw IOException("Could not read attributes from delegate filesystem.")
        attributes
    } else {
        throw IOException("Path was not a FileAttributeCachingPath, could not read attributes.")
    }

    /**
     * Read file [attributes] from the incoming [path]. If the returned attributes are `null` we then attempt to get
     * them from the [delegate] and populate the [path] with those attributes.
     *
     * The value returned will always be from the [path] parameter itself, never directly from the [delegate].
     *
     * @param path The [Path] to read file attributes from. It must be a [FileAttributeCachingPath] otherwise an
     * [IOException] will be thrown.
     * @param attributes The attributes to be retrieved from the [path]. Can be single attributes or an entire attribute
     * `Class` String (ie: "dos:*","basic:*","posix:permissions", etc.).
     * @param options The [LinkOption]s indicating how symbolic links are handled.
     * @return The file attributes for the given [path].
     * @throws IOException If the [path] is not a [FileAttributeCachingPath] or if something goes wrong with the
     * underlying calls to the delegate [FileSystemProvider].
     * @throws UnsupportedOperationException If the given [attributes] are not supported.
     */
    @Throws(IOException::class, UnsupportedOperationException::class)
    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): MutableMap<String, Any> = if (path is FileAttributeCachingPath) {
        val attributesMap = path.getAllAttributesMatchingName(attributes) {
            getAttributesClass(path, attributes)
        } ?: throw IOException("Could not read attributes from delegate filesystem.")
        attributesMap
    } else {
        throw IOException("Path was not a FileAttributeCachingPath, could not read attributes.")
    }

    override fun getFileSystem(uri: URI): FileSystem = delegate.getFileSystem(uri)

    override fun getPath(uri: URI): Path = delegate.getPath(uri)

    override fun getFileStore(path: Path): FileStore = delegate.getFileStore(path)

    /**
     * Set a single attribute for the given [path] first on the delegate [FileSystemProvider] and then in the [path].
     *
     * @param path The [Path] to set the given [attribute] on. It must be a [FileAttributeCachingPath] otherwise an
     * [IOException] will be thrown.
     * @param attribute The attribute name to set and associate with the [path].
     * @param value The value of the [attribute] to set.
     * @param options The [LinkOption]s indicating how symbolic links are handled.
     * @throws UnsupportedOperationException If the attribute view for the given [attribute] name is not available.
     * @throws IllegalArgumentException If the [attribute] name is not recognized or if its value is of the incorrect
     * type.
     */
    @Throws(IOException::class, UnsupportedOperationException::class)
    override fun setAttribute(path: Path, attribute: String, value: Any?, vararg options: LinkOption) {

        // Always set delegate attribute first with real file IO
        delegate.setAttribute(path, attribute, value, *options)

        // Then set our cache
        if (path is FileAttributeCachingPath) {

            val attributesObject = getAttributesClass(path, attribute)
            if (attributesObject != null) {
                path.setAttributeByName(attribute, attributesObject)
                // if the attributesObject is null we set its entry in the cache to null
            } else {
                path.setAttributeByName(attribute, null)
            }
        } else {
            throw IOException("Path was not a FileAttributeCachingPath, could not set attribute cache.")
        }
    }

    /**
     * Obtain the attribute `Class` for a given [path] and [attributes] String.
     *
     * @param path The [Path] to obtain the attribute `Class` from.
     * @param attributes The attributes used to look up the attribute `Class`. Can be single attributes or an entire
     * attribute `Class` String (ie: "dos:*","basic:*","posix:permissions", etc.).
     * @return The attribute `Class` for the [path] from the given [attributes] or `null` if the `Class` does not exist.
     * @throws IOException if an error occurs while trying to obtain the attribute `Class`.
     */
    @Throws(IOException::class)
    private fun getAttributesClass(path: Path, attributes: String): BasicFileAttributes? {
        val attributeView: Any = if (attributes.startsWith("dos")) {
            delegate.getFileAttributeView(path, DosFileAttributeView::class.java)
        } else if (attributes.startsWith("posix")) {
            delegate.getFileAttributeView(path, PosixFileAttributeView::class.java)
        } else {
            delegate.getFileAttributeView(path, BasicFileAttributeView::class.java)
        }

        return when (attributeView) {
            is DosFileAttributeView -> attributeView.readAttributes()
            is PosixFileAttributeView -> attributeView.readAttributes()
            is BasicFileAttributeView -> attributeView.readAttributes()
            else -> null
        }
    }

    /**
     * Set all attributes for a given [path].
     *
     * @param path The [Path] to set all attributes for. It must be a [FileAttributeCachingPath] otherwise an
     * [IOException] will be thrown.
     * @throws IOException if an I/O error occurs while accessing the various attribute views associated with the
     * [path].
     */
    @Throws(IOException::class)
    fun initializeCacheForPath(path: Path) {
        // set all attributes here in 3 io access calls (to get the views)
        if (path is FileAttributeCachingPath) {
            val dosAttributeView = delegate.getFileAttributeView(path, DosFileAttributeView::class.java)
            path.setAttributeByName("dos:*", dosAttributeView.readAttributes())

            val posixAttributeView = delegate.getFileAttributeView(path, PosixFileAttributeView::class.java)
            path.setAttributeByName("posix:*", posixAttributeView.readAttributes())

            val basicAttributeView = delegate.getFileAttributeView(path, BasicFileAttributeView::class.java)
            path.setAttributeByName("*", basicAttributeView.readAttributes())
        } else {
            throw IOException("Path was not a FileAttributeCachingPath, could not set attribute cache.")
        }
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) = delegate.move(source, target, *options)

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) = delegate.createDirectory(dir, *attrs)
}
