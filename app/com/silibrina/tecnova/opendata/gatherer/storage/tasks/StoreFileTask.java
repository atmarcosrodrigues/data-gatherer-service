package com.silibrina.tecnova.opendata.gatherer.storage.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.commons.fs.FileHandle;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.messenger.Message;
import com.silibrina.tecnova.commons.messenger.producer.ProducerService;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.file.FileMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import play.Logger;
import play.libs.Json;
import play.mvc.Http.MultipartFormData.FilePart;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

import static com.silibrina.tecnova.commons.exceptions.ExitStatus.CONFIGURATION_ERROR_STATUS;
import static com.silibrina.tecnova.commons.messenger.Message.MessageType.CREATE;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;

/**
 * Simple task to store a file and update {@link EntryMetadata} with the
 * extracted {@link FileMetadata}.
 */
public class StoreFileTask extends FileTask implements Callable<EntryMetadata> {
    private final static Logger.ALogger logger = Logger.of(StoreFileTask.class);
    private final ProducerService messenger;

    public StoreFileTask(FileSystem fileSystem, ProducerService messenger, FilePart src, EntryMetadata entry) {
        super(fileSystem, entry, src);

        checkNotNullCondition("messenger can not be null", CONFIGURATION_ERROR_STATUS, messenger);

        this.messenger = messenger;
    }

    @Override
    public EntryMetadata call() {
        EntryMetadata entry = getEntryMetadata();

        String originalName = getSrc().getFilename();

        try {
            FileHandle path = storeFile();
            messenger.publish(new Message(CREATE, entry._id().toHexString(), getPayload(originalName, path.toURI())));
        } catch (IOException e) {
            logger.error("An error occurred while trying to store the file: src: {}, entry: {}" ,
                    getSrc(), getEntryMetadata(), e);
        } catch (InterruptedException e) {
            logger.error("An interruption occurred while trying to publish to indexer. entry: {}" ,
                    getEntryMetadata(), e);
        } catch (URISyntaxException e) {
            logger.error("An error with the uri format. entry: {}", entry, e);
        }
        return new EntryMetadata().find(entry._id());
    }

    private JsonNode getPayload(String originalName, URI path) {
        ObjectNode payload = Json.newObject();
        payload.put(FileVersion.PATH, String.valueOf(path));
        payload.put(FileMetadata.ORIGINAL_NAME, originalName);
        return payload;
    }
}
