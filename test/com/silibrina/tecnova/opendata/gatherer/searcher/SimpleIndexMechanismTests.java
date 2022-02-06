package com.silibrina.tecnova.opendata.gatherer.searcher;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.coherence.indexer.LuceneIndexer;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.conversion.fs.parser.OriginalParser;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.test.WithApplication;

import java.io.File;

import static com.silibrina.tecnova.commons.conf.ConfigConstants.Strings.GATHERER_INDEX_DIR;
import static com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor.extractAttr;
import static com.silibrina.tecnova.commons.model.EntryMetadata.CONTENT;
import static com.silibrina.tecnova.commons.model.EntryMetadata.STATUS;
import static com.silibrina.tecnova.commons.model.EntryMetadata.Status.ALL;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getCurrentDir;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_1;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_2;
import static com.silibrina.tecnova.commons.utils.TestsUtils.cleanUpEntries;
import static com.silibrina.tecnova.commons.utils.TestsUtils.generateEntryMetadata;
import static com.silibrina.tecnova.commons.utils.TestsUtils.getFileSystemsToTest;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SimpleIndexMechanismTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(SimpleIndexMechanismTests.class);

    private SimpleSearchMechanism searchMechanism;
    private LuceneIndexer indexMechanism;

    private final FileSystem fileSystem;

    public SimpleIndexMechanismTests(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Before
    public void setUp() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        cleanUpEntries();

        Config config = ConfigFactory.load();

        searchMechanism = new SimpleSearchMechanism();
        String indexDir = config.getString(GATHERER_INDEX_DIR.field);
        indexMechanism = new LuceneIndexer(indexDir);
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        indexMechanism.close();
        cleanUpEntries();
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @Parameterized.Parameters
    public static FileSystem[] fileSystems() throws ConfigurationException {
        return getFileSystemsToTest();
    }

    @Test
    public void createIndexTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(1, 0, 0, PDF_FILE_1, fileSystem);
        entry1.save();

        createIndex(indexMechanism, entry1, fileSystem);
        indexMechanism.commit();
        assertEquals("should have indexed one file", 1, indexMechanism.size());

        EntryMetadata entry2 = generateEntryMetadata(2, 0, 0, PDF_FILE_2, fileSystem);
        entry2.save();

        createIndex(indexMechanism, entry2, fileSystem);
        indexMechanism.commit();
        assertEquals("should have indexed two files", 2, indexMechanism.size());
    }

    @Test
    public void parallelIndexingTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        int numThreads = 20;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(createIndexTask(i));
            threads[i] = thread;
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        EntryMetadata entry = generateEntryMetadata(21, 0, 0, PDF_FILE_1, fileSystem);
        entry.save();
        createIndex(indexMechanism, entry, fileSystem);
        indexMechanism.commit();
        assertEquals("should have 21 files indexed", 21, indexMechanism.size());
    }

    private Runnable createIndexTask(final int index) {
        return () -> {
            try {
                EntryMetadata entry = generateEntryMetadata(index, 0, 0, PDF_FILE_1, fileSystem);
                entry.save();
                createIndex(indexMechanism, entry, fileSystem);
            } catch (Throwable e) {
                logger.error("An error!", e);
            }
        };
    }

    @Test
    public void parallelIndexAndSearchTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        int numThreads = 40;
        Thread[] threads = new Thread[numThreads];
        EntryMetadata entry = generateEntryMetadata(41, 0, 0, PDF_FILE_1, fileSystem);
        entry.save();

        createIndex(indexMechanism, entry, fileSystem);
        indexMechanism.commit();

        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(createIndexAndSearchTask(i));
            threads[i] = thread;
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private Runnable createIndexAndSearchTask(final int index) {
        return () -> {
            try {
                if (index % 2 == 0) {
                    EntryMetadata entry = generateEntryMetadata(index, 0, 0, PDF_FILE_1, fileSystem);
                    entry.save();
                    createIndex(indexMechanism, entry, fileSystem);
                } else {
                    ObjectNode body = listAllQuery();
                    body.put(CONTENT, "paradigma");
                    searchMechanism.search(body);
                }
            } catch (Throwable e) {
                logger.error("An error!", e);
            }
        };
    }

    @Test
    public void updateIndexTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(1, 0, 0, PDF_FILE_1, fileSystem);
        entry1.save();

        createIndex(indexMechanism, entry1, fileSystem);
        indexMechanism.commit();
        assertEquals("should have indexed one file", 1, indexMechanism.size());

        EntryMetadata entry2 = generateEntryMetadata(2, 0, 0, PDF_FILE_2, fileSystem);
        entry2.save();

        createIndex(indexMechanism, entry2, fileSystem);
        indexMechanism.commit();
        assertEquals("should have indexed two files", 2, indexMechanism.size());

        ObjectNode searchBody = listAllQuery();
        searchBody.put(CONTENT, "octothorpe");
        searchBody.put(STATUS, String.valueOf(ALL));

        SearchResult foundItems = searchMechanism.search(searchBody).toCompletableFuture().get();
        assertNotNull("should have found some items or be empty", foundItems);
        assertEquals("should have found exactly one entry", 1, foundItems.getEntries().size());
        assertTrue("should have found entry2", foundItems.getEntries().contains(entry2));

        updateEntry(entry2, null, PDF_FILE_1, fileSystem);
        updateIndex(indexMechanism, entry2, fileSystem);
        indexMechanism.commit();


        FileVersion originalVersionEntry2 = entry2.getFileMetadata().getVersion(OriginalParser.TYPE);
        File originalFile = new File(getCurrentDir(PDF_FILE_1.path));
        assertEquals("Content of the files must be equal",
                extractAttr(originalFile).checksum, originalVersionEntry2.getHash());

        FileVersion originalVersionEntry1 = entry1.getFileMetadata().getVersion(OriginalParser.TYPE);
        assertEquals("Content of the files must be equal",
                originalVersionEntry1.getHash(), originalVersionEntry2.getHash());

        foundItems = searchMechanism.search(searchBody).toCompletableFuture().get();
        logger.debug("foundItems: {}", foundItems);
        assertNotNull("should have found some items or be empty", foundItems);
        assertEquals("should not have found entries", 0, foundItems.getEntries().size());

        searchBody.put(CONTENT, "paradigma");
        foundItems = searchMechanism.search(searchBody).toCompletableFuture().get();
        assertNotNull("should have found some items or be empty", foundItems);
        assertEquals("should have found all entries", 2, foundItems.getEntries().size());
    }

    @Test
    public void deleteIndexTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(1, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        indexMechanism.commit();
        assertEquals("should have indexed one file", 1, indexMechanism.size());

        EntryMetadata entry2 = generateEntryMetadata(2, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        indexMechanism.commit();
        assertEquals("should have indexed two files", 2, indexMechanism.size());

        ObjectNode searchBody = listAllQuery();
        searchBody.put(CONTENT, "octothorpe");
        searchBody.put(STATUS, String.valueOf(ALL));

        SearchResult foundItems = searchMechanism.search(searchBody).toCompletableFuture().get();
        assertNotNull("should have found some items or be empty", foundItems);
        assertEquals("should have found exactly one entry", 2, foundItems.getEntries().size());
        assertTrue("should have found entry2", foundItems.getEntries().contains(entry2));
        assertTrue("should have found entry1", foundItems.getEntries().contains(entry1));

        indexMechanism.removeIndex(entry1);
        indexMechanism.commit();

        foundItems = searchMechanism.search(searchBody).toCompletableFuture().get();
        assertNotNull("should have found some items or be empty", foundItems);
        assertEquals("should have found exactly one entry", 1, foundItems.getEntries().size());
        assertTrue("should have found entry2", foundItems.getEntries().contains(entry2));
        assertFalse("should not have found entry1", foundItems.getEntries().contains(entry1));
    }
}
