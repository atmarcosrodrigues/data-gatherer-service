package com.silibrina.tecnova.opendata.gatherer.storage.tasks;

import com.silibrina.tecnova.commons.fs.FileHandle;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.conversion.fs.parser.OriginalParser;
import play.Logger;
import play.mvc.Http.MultipartFormData.FilePart;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import static com.silibrina.tecnova.commons.exceptions.ExitStatus.CONFIGURATION_ERROR_STATUS;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getInputStream;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;

/**
 * Task that deals with file writing (create and update).
 */
abstract class FileTask {
    private static final Logger.ALogger logger = Logger.of(FileTask.class);

    private final FileSystem fileSystem;
    private final EntryMetadata entry;
    private final FilePart src;

    FileTask(FileSystem fileSystem, EntryMetadata entry, @Nonnull FilePart src) {
        checkNotNullCondition("file system can not be null", CONFIGURATION_ERROR_STATUS, fileSystem);
        checkNotNullCondition("entry can not be null", CONFIGURATION_ERROR_STATUS, entry);
        checkNotNullCondition("file can not be null", CONFIGURATION_ERROR_STATUS, src);

        this.fileSystem = fileSystem;
        this.entry = entry;
        this.src = src;
    }

    /**
     * Stores the current file to the given destination.
     *
     * @return The metadata extracted from this file.
     *
     * @throws IOException if an error happens while dealing with the file.
     */
    FileHandle storeFile() throws IOException {
        FileHandle dst = fileSystem.open(OriginalParser.TYPE, String.valueOf(entry._id()));

        URI uri = ((File) src.getFile()).toURI();

        logger.debug("src: {}, dst: {}, uri: {}", src, dst, uri);

        InputStream srcInputStream = getInputStream(uri);
        fileSystem.copy(srcInputStream, dst);

        return dst;
    }

    EntryMetadata getEntryMetadata() {
        return entry;
    }

    FilePart getSrc() {
        return src;
    }
}
