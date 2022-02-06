package com.silibrina.tecnova.opendata.gatherer;

import com.fasterxml.jackson.databind.JsonNode;
import com.silibrina.tecnova.commons.messenger.producer.ProducerService;
import com.silibrina.tecnova.commons.messenger.producer.SimpleProducerService;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.Response;
import com.silibrina.tecnova.opendata.gatherer.searcher.SearchMechanism;
import com.silibrina.tecnova.opendata.gatherer.searcher.SimpleSearchMechanism;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult;
import com.silibrina.tecnova.opendata.gatherer.storage.DataStorage;
import com.silibrina.tecnova.opendata.gatherer.storage.SimpleDataStorage;
import com.typesafe.config.ConfigFactory;
import play.Logger;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.mvc.Http.MultipartFormData.FilePart;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.io.Closeable;
import java.io.IOException;
import java.text.ParseException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static com.silibrina.tecnova.commons.conf.ConfigConstants.Booleans.STORAGE_SYNC;
import static com.silibrina.tecnova.commons.fs.FileSystemFactory.getFileSystem;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkValidString;
import static com.silibrina.tecnova.conversion.messenger.ConversionMessageConsumer.CONSUMER_QUEUE;

/**
 * Receives the request for data storage and access, extracts the metadata for proper persistence
 * and stores the raw data, enabling general access to entities.
 */
@Singleton
public class DataGatheringController implements Closeable {
    private static final Logger.ALogger logger = Logger.of(DataGatheringController.class);

    private boolean sync;
    private final DataStorage storage;
    private final ProducerService messenger;
    private final SearchMechanism searchMechanism;


    @SuppressWarnings("unused")
    @Inject
    public DataGatheringController(ApplicationLifecycle lifecycle) throws IOException, TimeoutException {
        sync = isSync();
        messenger = new SimpleProducerService(CONSUMER_QUEUE, sync);
        storage = new SimpleDataStorage(getFileSystem(), messenger);
        searchMechanism = new SimpleSearchMechanism();

        addStopHook(lifecycle);
    }

    DataGatheringController(ProducerService messenger,
                            DataStorage storage, SearchMechanism searchMechanism,
                            boolean sync) {
        this.messenger = messenger;
        this.storage = storage;
        this.searchMechanism = searchMechanism;
        this.sync = sync;
    }

    /**
     * Adds a hook that is executed when the application is stopping.
     *
     * @param lifecycle register to add a stop hook.
     */
    private void addStopHook(ApplicationLifecycle lifecycle) {
        lifecycle.addStopHook(() -> {
            close();
            return null;
        });
    }

    /**
     * Creates an entry.
     *
     * @param body parameters to create the entity.
     * @param file an optional file to attach to this entity.
     *             This file contains the real content to be made available.
     *
     * @return The saved entity as json.
     *
     * @throws IOException if an error occurs while dealing with the file.
     * @throws ParseException if an error occurs while parsing body to json or object.
     */
    public CompletionStage<Response<?>> create(final JsonNode body, final FilePart file) throws IOException, ParseException {
        logger.debug("body: {}, file: {}", body, debugFile(file));

        final EntryMetadata entry = EntryMetadata.buildEntryMetaData(body);

        return CompletableFuture.supplyAsync(() -> {
            try {
                entry.save();

                if (file == null) {
                    return new Response<>(entry.toJson());
                }

                CompletionStage<EntryMetadata> resultStoreFile = storage.storeFile(entry, file);

                return waitOrReturn(entry, resultStoreFile);
            } catch (Throwable e) {
                return new Response<>(e);
            }
        });
    }

    /**
     * Uploads a file associated with an entry. This method can also be used to update the file
     * of an entry.
     *
     * @param id the entity id to attach the file to.
     * @param file the file to be attached.
     *
     * @return a simple confirmation status.
     *
     */
    public CompletionStage<Response<?>> upload(final String id, final FilePart file) {
        logger.debug("id: {},  file: {}", id, debugFile(file));

        checkValidString("id can not be null", id);
        checkNotNullCondition("File can not be null", file);

        return CompletableFuture.supplyAsync(() -> {
            try {
                EntryMetadata entry = new EntryMetadata().find(id);
                checkNotNullCondition(String.format(Locale.getDefault(), "Entry not found [%s]", id), entry);

                CompletionStage<EntryMetadata> resultStoreFile = storage.updateFile(entry, file);
                return waitOrReturn(entry, resultStoreFile);
            } catch (Throwable e) {
                return new Response<>(e);
            }
        });
    }

    /**
     * Updates information of an entry.
     *
     * @param body entity parameters.
     *
     * @return the updated entity as json.
     *
     */
    public CompletionStage<Response<?>> update(@Nonnull final String id, @Nonnull final JsonNode body, final FilePart file) {
        logger.debug("id: {}, body: {}, file: {}", id, body, debugFile(file));

        checkNotNullCondition("body can not be null", body);

        return CompletableFuture.supplyAsync(() -> {
            try {
                EntryMetadata entry = new EntryMetadata().find(id);
                checkNotNullCondition(String.format(Locale.getDefault(), "Entry not found [%s]", id), entry);

                entry.updateEntry(body);
                entry.save();

                if (file == null) {
                    return new Response<>(entry.toJson());
                }

                CompletionStage<EntryMetadata> entryMetadataCompletionStage = storage.updateFile(entry, file);
                return waitOrReturn(entry, entryMetadataCompletionStage);
            } catch (Throwable e) {
                return new Response<>(e);
            }
        });
    }

    /**
     * Deletes an entry and its file.
     *
     * @param id the id of the entity to be deleted.
     *
     * @return a simple confirmation status.
     */
    public CompletionStage<Response<?>> delete(final String id) {
        logger.debug("id: {}", id);

        return CompletableFuture.supplyAsync(() -> {
            try {
                EntryMetadata entry = new EntryMetadata().find(id);
                checkNotNullCondition(String.format(Locale.getDefault(), "Entry not found [%s]", id), entry);

                CompletionStage<EntryMetadata> resultStoreFile = storage.deleteFile(entry);
                entry = sync ? resultStoreFile.toCompletableFuture().get() : entry;

                return new Response<>(Json.newObject()
                        .textNode(String.format(Locale.getDefault(),
                                "Entry deleted [%s]", entry._id())));
            } catch (Throwable e) {
                return new Response<>(e);
            }
        });
    }

    /**
     * List all registered entities. It is possible to pass the same parameters
     * given during creation to to search entries that matches them.
     * An exception is the 'content' param, which is used to search the
     * content of the files.
     *
     * @param body the body of the query. To know more about the params of this
     *             body, see constants in @{link EntryMetadata}.
     *
     * @throws IOException if an error occurs while dealing with the file.
     * @throws ParseException if an error occurs while parsing body to json or object.
     * @throws org.apache.lucene.queryparser.classic.ParseException if an error happens
     *                      trying to parse query to searcher.
     *
     * @return the entities found as json. Note that it does not retrieves the file.
     */
    public CompletionStage<Response<?>> list(final JsonNode body)
            throws org.apache.lucene.queryparser.classic.ParseException, ParseException, IOException {
        logger.debug("body: {}", body);
        checkNotNullCondition("body can not be null", body);

        CompletionStage<SearchResult> searchResult = searchMechanism.search(body);

        return searchResult.thenApply(result -> new Response<>(result.toJson()));
    }

    /**
     * Gets an entry by id.
     *
     * @param id the id of the entity to show.
     *
     * @return the found entity.
     */
    public CompletionStage<Response<?>> show(final String id) {
        logger.debug("id: {}", id);

        CompletionStage<Response<?>> responseCompletionStage = searchMechanism.find(id);
        return responseCompletionStage.thenApply(result -> {
            if (result.isException()) {
                return result;
            }

            EntryMetadata payload = (EntryMetadata) result.getPayload();
            return new Response<>(payload.toJson());
        });
    }

    @Override
    public void close() throws IOException {
        logger.debug("shutting down data gathering controller...");
        messenger.close();
        logger.debug("data gathering controller stopped.");
        logger.debug("bye, see ya!");
    }

    private boolean isSync() {
        return ConfigFactory.load().getBoolean(STORAGE_SYNC.field);
    }

    private Response<?> waitOrReturn(EntryMetadata entry, CompletionStage<EntryMetadata> resultStoreFile) {
        if (sync) {
            try {
                return new Response<>(resultStoreFile.toCompletableFuture().get().toJson());
            } catch (Throwable e) {
                return new Response<>(e);
            }
        }
        return new Response<>(entry.toJson());
    }

    private String debugFile(FilePart file) {
        if (file != null) {
            return file.getFilename();
        }
        return null;
    }
}
