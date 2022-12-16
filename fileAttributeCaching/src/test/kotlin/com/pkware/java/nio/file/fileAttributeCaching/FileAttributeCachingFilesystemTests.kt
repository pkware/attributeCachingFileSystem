package com.pkware.java.nio.file.fileAttributeCaching

import com.google.common.truth.ComparableSubject
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.CopyOption
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.DosFileAttributes
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.stream.Stream
import kotlin.io.path.exists

class FileAttributeCachingFilesystemTests {
    @ParameterizedTest
    @MethodSource("allTypes")
    fun <A : BasicFileAttributes?> `read attributes by class type from provider`(
        type: Class<A>,
        fileSystem: () -> FileSystem
    ) = fileSystem().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {

            // get file attribute caching path
            val cachingPath = it.getPath("testfile.txt")
            Files.writeString(cachingPath, "hello")
            // read file attributes for path from provider with the given class type
            val attributes = Files.readAttributes(cachingPath, type)
            // verify that attribute is "right" type returned from the provider
            assertThat(attributes).isInstanceOf(type)
        }
    }

    @ParameterizedTest
    @MethodSource("allNames")
    fun `read attributes by name from provider`(
        attributeName: String,
        expectedMapSize: Int,
        fileSystem: () -> FileSystem
    ) = fileSystem().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {

            // get file attribute caching path
            val cachingPath = it.getPath("testfile.txt")
            Files.writeString(cachingPath, "hello")
            // read file attributes for path from provider with the given name
            val attributesMap = Files.readAttributes(cachingPath, attributeName)
            // verify that attribute is "right" type returned from the provider
            assertThat(attributesMap).isInstanceOf(MutableMap::class.java)
            assertThat(attributesMap.size).isEqualTo(expectedMapSize)
        }
    }

    @ParameterizedTest
    @MethodSource("posixFileSystems")
    fun `set posix attributes for path`(
        fileSystem: () -> FileSystem
    ) = fileSystem().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {

            // get file attribute caching path
            val cachingPath = it.getPath("testfile.txt")
            Files.writeString(cachingPath, "hello")
            val lookupService = it.userPrincipalLookupService
            val owner = lookupService.lookupPrincipalByName("testUser")
            val group = lookupService.lookupPrincipalByGroupName("testGroup")
            val permissions = EnumSet.of(
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_WRITE,
            )

            Files.setAttribute(cachingPath, "posix:owner", owner)
            Files.setAttribute(cachingPath, "posix:group", group)
            Files.setAttribute(cachingPath, "posix:permissions", permissions)
            val attributesMap = Files.readAttributes(cachingPath, "posix:*")

            assertThat(attributesMap.size).isEqualTo(12)
            val ownerUserPrincipal = attributesMap["posix:owner"] as UserPrincipal
            assertThat(ownerUserPrincipal.name).isEqualTo(owner.name)
            val groupUserPrincipal = attributesMap["posix:group"] as GroupPrincipal
            assertThat(groupUserPrincipal.name).isEqualTo(group.name)
            @Suppress("UNCHECKED_CAST")
            assertThat(
                PosixFilePermissions.toString(attributesMap["posix:permissions"] as? MutableSet<PosixFilePermission>)
            ).isEqualTo(
                PosixFilePermissions.toString(permissions)
            )
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "dos:readonly",
            "dos:hidden",
            "dos:archive",
            "dos:archive",
        ]
    )
    fun `set and read dos boolean attributes for path`(
        attributeName: String,
    ) = windowsJimfs().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {

            // get file attribute caching path
            val cachingPath = it.getPath("testfile.txt")
            Files.writeString(cachingPath, "hello")

            Files.setAttribute(cachingPath, attributeName, true)
            val attributesMap = Files.readAttributes(cachingPath, attributeName)

            assertThat(attributesMap.size).isEqualTo(1)
            assertThat(attributesMap[attributeName]).isEqualTo(true)
        }
    }

    @ParameterizedTest
    @MethodSource("allFileSystemsWithCopyOption")
    fun `copy file from source to target`(
        option: CopyOption,
        fileSystem: () -> FileSystem
    ) = fileSystem().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {
            // get file attribute caching path
            val cachingPath = it.getPath("testfile.txt")
            Files.writeString(cachingPath, "hello")
            Files.setAttribute(cachingPath, "creationTime", testDateFileTime)
            Files.setAttribute(cachingPath, "lastModifiedTime", testDateFileTime)
            Files.setAttribute(cachingPath, "lastAccessTime", testDateFileTime)

            val destinationCachingPath = it.getPath("testfile2.txt")

            assertThat(destinationCachingPath.exists()).isEqualTo(false)

            Files.copy(cachingPath, destinationCachingPath, option)

            assertThat(cachingPath.exists()).isEqualTo(true)

            val writtenValue = Files.readString(destinationCachingPath)
            val basicFileAttributes = Files.readAttributes(destinationCachingPath, "*")

            assertThat(writtenValue).isEqualTo("hello")
            val creationTime = basicFileAttributes["creationTime"] as FileTime
            assertThat(creationTime).followedFlagRulesComparedTo(option, testDateFileTime)
            val lastModifiedTime = basicFileAttributes["lastModifiedTime"] as FileTime
            assertThat(lastModifiedTime).followedFlagRulesComparedTo(option, testDateFileTime)
            val lastAccessTime = basicFileAttributes["lastAccessTime"] as FileTime
            assertThat(lastAccessTime).followedFlagRulesComparedTo(option, testDateFileTime)
        }
    }

    @ParameterizedTest
    @MethodSource("allFileSystemsWithCopyOption")
    fun `move file from source to target`(
        option: CopyOption,
        fileSystem: () -> FileSystem
    ) = fileSystem().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {
            // get file attribute caching path
            val cachingPath = it.getPath("testfile.txt")
            Files.writeString(cachingPath, "hello")
            Files.setAttribute(cachingPath, "creationTime", testDateFileTime)
            Files.setAttribute(cachingPath, "lastModifiedTime", testDateFileTime)
            Files.setAttribute(cachingPath, "lastAccessTime", testDateFileTime)

            // ensure temp directory exists
            Files.createDirectory(it.getPath("temp"))
            val destinationCachingPath = it.getPath("temp", "testfile2.txt")

            assertThat(destinationCachingPath.exists()).isEqualTo(false)

            Files.move(cachingPath, destinationCachingPath, option)

            assertThat(cachingPath.exists()).isEqualTo(false)

            val writtenValue = Files.readString(destinationCachingPath)
            val basicFileAttributes = Files.readAttributes(destinationCachingPath, "*")

            assertThat(writtenValue).isEqualTo("hello")

            // creation and move time are preserved for a move regardless of the option flag used
            assertThat(basicFileAttributes["creationTime"]).isEqualTo(testDateFileTime)
            assertThat(basicFileAttributes["lastModifiedTime"]).isEqualTo(testDateFileTime)
            val lastAccessTime = basicFileAttributes["lastAccessTime"] as FileTime
            assertThat(lastAccessTime).followedFlagRulesComparedTo(option, testDateFileTime)
        }
    }

    @DisabledOnOs(OS.MAC, OS.LINUX)
    @ParameterizedTest
    @MethodSource("hiddenTestPathsWindows")
    fun `file isHidden on windows`(fileName: String, expectedHidden: Boolean) = windowsJimfs().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {
            val directoryName = "temp"
            Files.createDirectory(it.getPath(directoryName))
            val cachingPath = it.getPath(directoryName, fileName)
            if (fileName.isNotEmpty()) Files.writeString(cachingPath, "hello")
            Files.setAttribute(cachingPath, "dos:hidden", true)
            assertThat(Files.isHidden(cachingPath)).isEqualTo(expectedHidden)
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest
    @MethodSource("hiddenTestPathsPosix")
    fun `file isHidden on unix and macOS`(
        fileName: String,
        expectedHidden: Boolean,
        fileSystem: () -> FileSystem
    ) = fileSystem().use { jimfs ->
        FileAttributeCachingFileSystem.wrapping(jimfs).use {
            val directoryName = "temp"
            Files.createDirectory(it.getPath(directoryName))
            val cachingPath = it.getPath(directoryName, fileName)
            assertThat(Files.isHidden(cachingPath)).isEqualTo(expectedHidden)
        }
    }

    companion object {
        @JvmStatic
        fun allTypes(): Stream<Arguments> = Stream.of(
            Arguments.arguments(BasicFileAttributes::class.java, ::windowsJimfs),
            Arguments.arguments(DosFileAttributes::class.java, ::windowsJimfs),
            Arguments.arguments(PosixFileAttributes::class.java, ::linuxJimfs),
            Arguments.arguments(PosixFileAttributes::class.java, ::osXJimfs),
        )

        @JvmStatic
        fun allNames(): Stream<Arguments> = Stream.of(
            Arguments.arguments("*", 9, ::windowsJimfs),
            Arguments.arguments("dos:*", 13, ::windowsJimfs),
            Arguments.arguments("posix:*", 12, ::linuxJimfs),
            Arguments.arguments("posix:*", 12, ::osXJimfs),
        )

        @JvmStatic
        fun posixFileSystems(): Stream<Arguments> = Stream.of(
            Arguments.arguments(::linuxJimfs),
            Arguments.arguments(::osXJimfs),
        )

        @JvmStatic
        fun hiddenTestPathsWindows(): Stream<Arguments> = Stream.of(
            Arguments.arguments("test1.txt", true),
            // blank file name for a directory, directories can never be hidden
            Arguments.arguments("", false),
        )

        @JvmStatic
        fun hiddenTestPathsPosix(): Stream<Arguments> = Stream.of(
            Arguments.arguments(".test1.txt", true, ::linuxJimfs),
            Arguments.arguments(".test1.txt", true, ::osXJimfs),
            Arguments.arguments("test2.txt", false, ::linuxJimfs),
            Arguments.arguments("test2.txt", false, ::osXJimfs),
        )

        @JvmStatic
        fun allFileSystemsWithCopyOption(): Stream<Arguments> = Stream.of(
            Arguments.arguments(StandardCopyOption.REPLACE_EXISTING, ::windowsJimfs),
            Arguments.arguments(StandardCopyOption.COPY_ATTRIBUTES, ::windowsJimfs),
            Arguments.arguments(StandardCopyOption.REPLACE_EXISTING, ::linuxJimfs),
            Arguments.arguments(StandardCopyOption.COPY_ATTRIBUTES, ::linuxJimfs),
            Arguments.arguments(StandardCopyOption.REPLACE_EXISTING, ::osXJimfs),
            Arguments.arguments(StandardCopyOption.COPY_ATTRIBUTES, ::osXJimfs),
        )

        private fun ComparableSubject<FileTime>.followedFlagRulesComparedTo(
            options: CopyOption,
            expected: FileTime
        ) = if (options == StandardCopyOption.COPY_ATTRIBUTES) isEqualTo(expected) else isNotEqualTo(expected)
    }
}

/**
 * A [FileTime] representing the date "01/01/1970, 07:34:27 PM".
 */
private val testDateFileTime: FileTime = FileTime.from(
    SimpleDateFormat("MM/dd/yyyy, hh:mm:ss a").parse("01/01/1970, 07:34:27 PM").toInstant()
)
