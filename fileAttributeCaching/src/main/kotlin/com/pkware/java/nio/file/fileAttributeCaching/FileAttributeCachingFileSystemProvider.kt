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
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.spi.FileSystemProvider

/**
 * TODO document.
 * TODO need to figure out how to have this class as one that only overrides the changed functions rather than everything for FileSystemProvider.
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

    // We get the attribute from the cache and if it does not exist/expired cache gets attribute from delegate, and the
    // return value is always from the cache
    /**
     * TODO document.
     */
    @Throws(IOException::class)
    override fun <A : BasicFileAttributes?> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption
    ): A = if (path is FileAttributeCachingPath) {
        val attributes = path.getAllAttributesMatchingClass(type) {
            delegate.readAttributes(path, type, *options)
        } ?: throw IOException("Could not read attributes from delegate filesystem.")
        attributes

            /*var attributes = path.getAllAttributesMatchingClass(type)
            if( attributes == null ){
                //set our attribute class in the cache if it does not exist
                path.setAttributeByType(type, delegate.readAttributes(path, type, *options))
                attributes = path.getAllAttributesMatchingClass(type)
                if( attributes == null ){
                    throw IOException("Could not read attributes from delegate filesystem.")
                }
            }
            attributes*/
    } else {
        throw IOException("Path was not a FileAttributeCachingPath, could not read attributes.")
    }

    // We get the attribute from the cache and if it does not exist/expired cache gets attribute from delegate, and the
    // return value is always from the cache
    /**
     * TODO document.
     */
    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption
    ): MutableMap<String, Any> = if (path is FileAttributeCachingPath) {
        val attributesMap = path.getAllAttributesMatchingName(attributes) {
            getAttributesClass(path, attributes)
        } ?: throw IOException("Could not read attributes from delegate filesystem.")
        attributesMap

            /*var attributesMap = path.getAllAttributesMatchingName(attributes)
            if( attributesMap == null ){
                val attributesObject = getAttributesClass(path, attributes)
                if(attributesObject != null){
                    path.setAttributeByName(attributes, attributesObject)
                }

                attributesMap = path.getAllAttributesMatchingName(attributes)
                if( attributesMap == null ){
                    throw IOException("Could not read attributes from delegate filesystem.")
                }
            }
            attributesMap*/
    } else {
        throw IOException("Path was not a FileAttributeCachingPath, could not read attributes.")
    }

    override fun getFileSystem(uri: URI): FileSystem = delegate.getFileSystem(uri)

    override fun getPath(uri: URI): Path = delegate.getPath(uri)

    override fun getFileStore(path: Path): FileStore = delegate.getFileStore(path)

    /**
     * TODO document.
     */
    override fun setAttribute(path: Path, attribute: String, value: Any?, vararg options: LinkOption) {

        // Always set delegate attribute first with real file IO
        delegate.setAttribute(path, attribute, value, *options)

        // Then set our cache
        if (path is FileAttributeCachingPath) {

            val attributesObject = getAttributesClass(path, attribute)
            if (attributesObject != null) {
                path.setAttributeByName(attribute, attributesObject)
            } else {
                path.setAttributeByName(attribute, null)
            }
        }
    }

    // This function does 1 fileIO access to get the right view
    /**
     * TODO document.
     */
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
     * TODO document.
     */
    fun initializeCacheForPath(path: Path) {
        // set all attributes here in 3 io access calls (to get the views)
        if (path is FileAttributeCachingPath) {
            val dosAttributeView = delegate.getFileAttributeView(path, DosFileAttributeView::class.java)
            path.setAttributeByName("dos:*", dosAttributeView.readAttributes())

            val posixAttributeView = delegate.getFileAttributeView(path, PosixFileAttributeView::class.java)
            path.setAttributeByName("posix:*", posixAttributeView.readAttributes())

            val basicAttributeView = delegate.getFileAttributeView(path, BasicFileAttributeView::class.java)
            path.setAttributeByName("*", basicAttributeView.readAttributes())
        }
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) = delegate.move(source, target, *options)

    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) = delegate.createDirectory(dir, *attrs)
}
