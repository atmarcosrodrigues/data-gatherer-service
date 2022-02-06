package com.silibrina.tecnova.opendata.gatherer.storage.tasks;

import com.silibrina.tecnova.commons.messenger.Message;
import com.silibrina.tecnova.commons.messenger.producer.ProducerService;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import play.Logger;
import play.libs.Json;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.Callable;

import static com.silibrina.tecnova.commons.messenger.Message.MessageType.DELETE;

/**
 * Simple task to delete a file and its dependencies.
 */
public class  DeleteFileTask implements Callable<EntryMetadata> {
    private final static Logger.ALogger logger = Logger.of(DeleteFileTask.class);

    private final ProducerService messenger;
    private final EntryMetadata entry;

    public DeleteFileTask(@Nonnull ProducerService messenger, @Nonnull EntryMetadata entry) {
        this.messenger = messenger;
        this.entry = entry;
    }

    @Override
    public EntryMetadata call() {
        try {
            messenger.publish(new Message(DELETE, entry._id().toHexString(), Json.newObject()));
        } catch (IOException e) {
            logger.error("An error occurred while trying to delete the file: (entry: {})", entry, e);
        } catch (InterruptedException e) {
            logger.error("An interruption occurred while trying to publish to indexer. entry: {}" ,
                    entry, e);
        }

        return entry;
    }
}
