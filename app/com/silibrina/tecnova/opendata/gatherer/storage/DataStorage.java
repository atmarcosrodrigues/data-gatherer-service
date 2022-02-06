package com.silibrina.tecnova.opendata.gatherer.storage;

import com.silibrina.tecnova.commons.model.EntryMetadata;
import play.mvc.Http.MultipartFormData.FilePart;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletionStage;

/**
 * Properly stores a file in a file system. Note that, a request
 * to store a file might be handle asynchronously.
 */
public interface DataStorage {

    /**
     * Stores a file for future access.
     *
     * @param entry the entry which the file is attached to.
     * @param file a file to be stored.
     *
     * @return the path for access this file.
     */
    CompletionStage<EntryMetadata> storeFile(@Nonnull EntryMetadata entry, @Nonnull FilePart file);

    /**
     * Updates an attached file. The behaviour of this method can be mimic by
     * deleting and creating a new file.
     *
     * @param entry the entry which the file is attached to.
     * @param file a file to be stored.
     *
     * @return the path to access this file.
     */
    CompletionStage<EntryMetadata> updateFile(@Nonnull EntryMetadata entry, @Nonnull FilePart file);

    /**
     * Deletes a stored file.
     *
     * @param entry to delete with its file.
     */
    CompletionStage<EntryMetadata> deleteFile(@Nonnull EntryMetadata entry);
}
