package com.silibrina.tecnova.opendata.gatherer;

import com.google.common.io.Closeables;
import com.silibrina.tecnova.coherence.CoherenceMain;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.messenger.producer.SimpleProducerService;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.commons.utils.CommonsFileUtils;
import com.silibrina.tecnova.conversion.ConversionMain;
import com.silibrina.tecnova.conversion.fs.parser.OriginalParser;
import com.silibrina.tecnova.opendata.gatherer.storage.DataStorage;
import com.silibrina.tecnova.opendata.gatherer.storage.SimpleDataStorage;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.test.WithApplication;

import java.io.*;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor.extractAttr;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getCurrentDir;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getInputStream;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.XLS_FILE_1;
import static com.silibrina.tecnova.commons.utils.TestsUtils.*;
import static com.silibrina.tecnova.conversion.messenger.ConversionMessageConsumer.CONSUMER_QUEUE;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.getFileAsFilePart;
import static org.apache.commons.io.FileUtils.readLines;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SimpleDataStorageTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(SimpleDataStorageTests.class);

    private ConversionMain conversionMain;
    private CoherenceMain coherenceMain;

    private final FileSystem fileSystem;

    public SimpleDataStorageTests(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Before
    public void setUp() throws IOException, TimeoutException, ConfigurationException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        cleanUpEntries();

        coherenceMain = new CoherenceMain(false);
        coherenceMain.start();

        conversionMain = new ConversionMain(false, fileSystem);
        conversionMain.start();

        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws IOException, ConfigurationException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        conversionMain.stopConverter();
        coherenceMain.stopCoherence();
        cleanUpEntries();
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @Parameterized.Parameters
    public static FileSystem[] fileSystems() throws ConfigurationException {
        return getFileSystemsToTest();
    }

    @Test
    public void createNewInstance() throws IOException, TimeoutException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleProducerService messenger = null;
        try {
            messenger = new SimpleProducerService(CONSUMER_QUEUE + "some", true);
            new SimpleDataStorage(fileSystem, messenger);
        } finally {
            Closeables.close(messenger, false);
        }
    }

    @Test
    public void createFileTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        DataStorage storage = new SimpleDataStorage(fileSystem, new SimpleProducerService(CONSUMER_QUEUE, true));

        EntryMetadata entry = generateEntryMetadata(1, 0, 0, null, fileSystem);
        entry.save();
        assertNull("fileMetadata should be null", entry.getFileMetadata());

        EntryMetadata fileMetadata = storage.storeFile(entry, getFileAsFilePart(XLS_FILE_1.path)).toCompletableFuture().get();
        entry = new EntryMetadata().find(entry._id());

        assertNotNull("fileMetadata should not be null", fileMetadata.getFileMetadata());
        assertEquals("should be the same fileMetadata", fileMetadata.getFileMetadata(), entry.getFileMetadata());
    }

    @Test
    public void retrieveFileTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry = generateEntryMetadata(1, 0, 0, XLS_FILE_1, fileSystem);

        URI path = entry.getFileMetadata().getVersion(OriginalParser.TYPE).getPath();

        try (InputStream inputStream = CommonsFileUtils.getInputStream(path)) {
            assertEquals("Content of the files must be equal",
                    readLines(new File(getCurrentDir(XLS_FILE_1.path))),
                    IOUtils.readLines(inputStream));
        }
    }

    @Test
    public void deleteFileTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        try (SimpleProducerService messenger = new SimpleProducerService(CONSUMER_QUEUE, true)) {
            DataStorage storage = new SimpleDataStorage(fileSystem, messenger);
            EntryMetadata entry = generateEntryMetadata(1, 0, 0, XLS_FILE_1, fileSystem);
            entry.save();

            File file = new File(ODFileUtils.getCurrentDir(XLS_FILE_1.path));
            try (InputStream inputStream = getInputStreamFromEntry(entry)) {
                assertEquals("Content of the files must be equal", extractAttr(file), extractAttr(inputStream));
            }

            storage.deleteFile(entry).toCompletableFuture().get();

            FileVersion originalVersion = entry.getFileMetadata().getVersion(OriginalParser.TYPE);
            assertFalse("file should have been deleted", fileVersionExists(originalVersion));
        }
    }

    private InputStream getInputStreamFromEntry(EntryMetadata entry) throws IOException {
        FileVersion originalVersion = entry.getFileMetadata().getVersion(OriginalParser.TYPE);
        return getInputStream(originalVersion.getPath());
    }
}
