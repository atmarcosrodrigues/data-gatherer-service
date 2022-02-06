package com.silibrina.tecnova.opendata.gatherer.storage;

import com.silibrina.tecnova.commons.exceptions.UnrecoverableErrorException;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.messenger.producer.ProducerService;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.opendata.executors.ExecutorFactory;
import com.silibrina.tecnova.opendata.gatherer.storage.tasks.DeleteFileTask;
import com.silibrina.tecnova.opendata.gatherer.storage.tasks.StoreFileTask;
import com.silibrina.tecnova.opendata.gatherer.storage.tasks.UpdateFileTask;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import play.Logger;
import play.mvc.Http.MultipartFormData.FilePart;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

import static com.silibrina.tecnova.commons.conf.ConfigConstants.Strings.LOCAL_STORAGE_BASEDIR;
import static com.silibrina.tecnova.commons.exceptions.ExitStatus.IO_ERROR_STATUS;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;
import static com.silibrina.tecnova.opendata.executors.ExecutorFactory.ExecutorType.STORAGE;

/**
 * This is the file storage logic for local file system.
 *
 * (ex: using LocalFileSystem and SwiftFileSystem).
 */
public class SimpleDataStorage implements DataStorage {
    private final static Logger.ALogger logger = Logger.of(SimpleDataStorage.class);

    private final FileSystem fileSystem;
    private final ProducerService messenger;
    private final Executor executor;

    public SimpleDataStorage(FileSystem fileSystem, ProducerService messenger) {
        this.fileSystem = fileSystem;
        this.messenger = messenger;

        executor = ExecutorFactory.getExecutor(STORAGE);

        mkdir();
    }

    @Override
    public CompletionStage<EntryMetadata> storeFile(@Nonnull EntryMetadata entry, @Nonnull FilePart file) {
        checkNotNullCondition("Entry can not be null", entry);
        checkNotNullCondition("File can not be null", file);
        return CompletableFuture.supplyAsync(() -> new StoreFileTask(fileSystem, messenger, file, entry).call(), executor);
    }

    @Override
    public CompletionStage<EntryMetadata> updateFile(@Nonnull EntryMetadata entry, @Nonnull FilePart file) {
        checkNotNullCondition("Entry can not be null", entry);
        checkNotNullCondition("File can not be null", file);
        return CompletableFuture.supplyAsync(() -> new UpdateFileTask(fileSystem, messenger, file, entry).call(), executor);
    }

    @Override
    public CompletionStage<EntryMetadata> deleteFile(@Nonnull EntryMetadata entry) {
        checkNotNullCondition("Entry can not be null", entry);
        return CompletableFuture.supplyAsync(() -> new DeleteFileTask(messenger, entry).call(), executor);
    }

    private void mkdir() {
        String path = ConfigFactory.load().getString(LOCAL_STORAGE_BASEDIR.field);
        try {

            FileUtils.forceMkdir(new File(path));
        } catch (IOException e) {
            logger.error("error while creating storage dir - path {}", path, e);
            throw new UnrecoverableErrorException("(" + path + ") IOException: " + e.getMessage(), IO_ERROR_STATUS);
        }
    }

}
