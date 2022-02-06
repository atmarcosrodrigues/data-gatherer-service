package com.silibrina.tecnova.opendata.gatherer.searcher;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.coherence.indexer.LuceneIndexer;
import com.silibrina.tecnova.coherence.utils.CoherenceTestsUtils;
import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.libs.Json;
import play.test.WithApplication;

import java.io.IOException;
import java.text.SimpleDateFormat;

import static com.silibrina.tecnova.commons.model.EntryMetadata.*;
import static com.silibrina.tecnova.commons.model.EntryMetadata.Status.*;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_1;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_2;
import static com.silibrina.tecnova.commons.utils.TestsUtils.*;
import static com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchEntryTask.*;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SimpleSearchMechanismTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(SimpleSearchMechanismTests.class);

    private final SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

    private SimpleSearchMechanism searchMechanism;
    private LuceneIndexer indexMechanism;
    private final FileSystem fileSystem;

    public SimpleSearchMechanismTests(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Before
    public void setUp() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        cleanUpEntries();

        indexMechanism = new LuceneIndexer(CoherenceTestsUtils.getIndexDir());
        searchMechanism = new SimpleSearchMechanism();

        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws IOException, ConfigurationException {
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
    public void searcherTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(CONTENT, "octothorpe");
        json.put(STATUS, String.valueOf(ALL));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 1, entries.getEntries().size());
        assertTrue("should have found entry2", entries.getEntries().get(0).equals(entry2));
    }

    @Test(expected = InvalidConditionException.class)
    public void searcherNullTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);

        searchMechanism.search(null);
    }

    @Test
    public void searchBeforeIndexTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SearchResult entries = searchMechanism.search(listAllQuery()).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertTrue("should have found no entry", entries.getEntries().isEmpty());
    }

    @Test
    public void searcherRestrictByFields() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        ObjectNode body2 = generateBody(4, 0, 0);
        EntryMetadata entry2 = createEntry(body2, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        ObjectNode body3 = generateBody(5, 0, 0);
        body3.put("author", body2.get("author").asText());
        body3.put("org", body2.get("org").asText());
        body3.put("org", body2.get("org").asText());
        body3.put("uploader", body2.get("uploader").asText());
        EntryMetadata entry3 = createEntry(body3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        EntryMetadata entry4 = createEntry(body3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry4, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(CONTENT, "octothorpe");
        json.put(STATUS, String.valueOf(ALL));
        json.put(ORG, entry3.getOrg());
        json.put(TITLE, entry3.getTitle());
        json.put(UPLOADER, entry3.getUploader());

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry3", entries.getEntries().contains(entry3));
        assertTrue("should have found entry4", entries.getEntries().contains(entry4));
    }

    @Test
    public void searcherRestrictByPeriod() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(4, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(CONTENT, "octothorpe");
        json.put(STATUS, String.valueOf(ALL));
        json.put(PERIOD_START, formatter.format(generateDate(0)));
        json.put(PERIOD_END, formatter.format(generateDate(4)));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 1, entries.getEntries().size());
        assertTrue("should have found entry3", entries.getEntries().get(0).equals(entry3));
    }

    @Test
    public void searcherRestrictByPeriodNoContent() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(4, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(STATUS, String.valueOf(ALL));
        json.put(PERIOD_START, formatter.format(generateDate(0)));
        json.put(PERIOD_END, formatter.format(generateDate(4)));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry3", entries.getEntries().contains(entry3));
        assertTrue("should have found entry3", entries.getEntries().contains(entry1));
    }

    @Test
    public void searcherRestrictByStart() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(4, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(STATUS, String.valueOf(ALL));
        json.put(CONTENT, "octothorpe");
        json.put(PERIOD_START, formatter.format(generateDate(0)));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry3", entries.getEntries().contains(entry3));
        assertTrue("should have found entry1", entries.getEntries().contains(entry1));
    }

    @Test
    public void searcherRestrictByEnd() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(4, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(STATUS, String.valueOf(ALL));
        json.put(PERIOD_END, formatter.format(generateDate(1)));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry1", entries.getEntries().contains(entry1));
        assertTrue("should have found entry2", entries.getEntries().contains(entry2));
    }

    @Test
    public void searchEmptyQuery() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(4, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(STATUS, String.valueOf(ALL));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();

        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found all entries", 3, entries.getEntries().size());
    }

    @Test
    public void executeMultiplesSearch() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(4, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        indexMechanism.commit();

        int numThreads = 20;
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            Thread thread = new Thread(() -> {
                try {
                    ObjectNode query = listAllQuery();
                    query.put(CONTENT, "octothorpe");
                    query.put(STATUS, String.valueOf(ALL));
                    assertEquals("Should always find all 3 occurrences", 3,
                            searchMechanism.search(query).toCompletableFuture().get().getEntries().size());
                } catch (Throwable e) {
                    logger.error("An error!", e);
                }
            });
            threads[i] = thread;
            thread.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }

    @Test
    public void limitResultTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(5, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        EntryMetadata entry4 = generateEntryMetadata(6, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry4, fileSystem);
        EntryMetadata entry5 = generateEntryMetadata(7, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry5, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(LIMIT, 2);
        json.put(STATUS, String.valueOf(ALL));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry1", entries.getEntries().contains(entry1));
        assertTrue("should have found entry2", entries.getEntries().contains(entry2));
    }

    @Test
    public void pageResultTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(5, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        EntryMetadata entry4 = generateEntryMetadata(6, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry4, fileSystem);
        EntryMetadata entry5 = generateEntryMetadata(7, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry5, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(PAGE, 1);
        json.put(LIMIT, 2);
        json.put(STATUS, String.valueOf(ALL));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry1", entries.getEntries().contains(entry3));
        assertTrue("should have found entry2", entries.getEntries().contains(entry4));
    }

    @Test
    public void orderByResultTestAsc() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(5, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        EntryMetadata entry4 = generateEntryMetadata(6, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry4, fileSystem);
        EntryMetadata entry5 = generateEntryMetadata(7, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry5, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(PAGE, 0);
        json.put(LIMIT, 2);
        json.put(ORDER_BY, EntryMetadata.CREATED_AT);
        json.put(ORDER, "ASC");
        json.put(STATUS, String.valueOf(ALL));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry1", entries.getEntries().contains(entry1));
        assertTrue("should have found entry2", entries.getEntries().contains(entry2));
    }

    @Test
    public void orderByResultTestDesc() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry1, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, -1, 1, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry2, fileSystem);
        EntryMetadata entry3 = generateEntryMetadata(5, 1, 3, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry3, fileSystem);
        EntryMetadata entry4 = generateEntryMetadata(6, 2, 4, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry4, fileSystem);
        EntryMetadata entry5 = generateEntryMetadata(7, 3, 5, PDF_FILE_2, fileSystem);
        createIndex(indexMechanism, entry5, fileSystem);
        indexMechanism.commit();

        ObjectNode json = listAllQuery();
        json.put(PAGE, 0);
        json.put(LIMIT, 2);
        json.put(ORDER, "DESC");
        json.put(ORDER_BY, EntryMetadata.CREATED_AT);
        json.put(STATUS, String.valueOf(ALL));

        SearchResult entries = searchMechanism.search(json).toCompletableFuture().get();
        assertNotNull("the answer should not be null", entries);
        assertEquals("should have found exactly one entry", 2, entries.getEntries().size());
        assertTrue("should have found entry1", entries.getEntries().contains(entry4));
        assertTrue("should have found entry2", entries.getEntries().contains(entry5));
    }

    @Test
    public void searchByDefaultStatus() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        SearchResult result = searchMechanism.search(Json.newObject()).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertTrue("result should be empty", result.getEntries().isEmpty());

        createIndex(indexMechanism, entry, fileSystem);
        indexMechanism.commit();
        entry.setStatus(READY);
        entry.save();

        result = searchMechanism.search(Json.newObject()).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
    }

    @Test
    public void searchByReadyStatus() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        ObjectNode query = Json.newObject();
        query.put(STATUS, READY.toString());
        SearchResult result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertTrue("result should be empty", result.getEntries().isEmpty());

        createIndex(indexMechanism, entry, fileSystem);
        indexMechanism.commit();
        entry.setStatus(READY);
        entry.save();

        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
    }

    @Test
    public void searchByMissingFileStatus() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, null, fileSystem);

        ObjectNode query = Json.newObject();
        query.put(STATUS, READY.toString());
        SearchResult result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertTrue("result should be empty", result.getEntries().isEmpty());

        createIndex(indexMechanism, entry1, fileSystem);
        indexMechanism.commit();
        entry1.setStatus(READY);
        entry1.save();

        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
        assertEquals("should be entry1", entry1, result.getEntries().get(0));

        query.put(STATUS, String.valueOf(TO_INDEX));
        result = searchMechanism.search(query).toCompletableFuture().get();

        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
        assertEquals("should be entry2", entry2, result.getEntries().get(0));
    }

    @Test
    public void searchByToIndexFileStatus() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        entry1.save();
        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);
        entry2.save();

        ObjectNode query = Json.newObject();
        query.put(STATUS, READY.toString());
        SearchResult result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertTrue("result should be empty", result.getEntries().isEmpty());

        query.put(STATUS, TO_INDEX.toString());
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertEquals("should have to entries to index", 2, result.getEntries().size());

        createIndex(indexMechanism, entry1, fileSystem);
        indexMechanism.commit();
        entry1.setStatus(READY);
        entry1.save();

        query.put(STATUS, READY.toString());
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
        assertEquals("should be entry1", entry1, result.getEntries().get(0));

        query.put(STATUS, TO_INDEX.toString());
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
        assertEquals("should be entry2", entry2, result.getEntries().get(0));
    }

    @Test
    public void searchByAllFileStatus() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_2, fileSystem);
        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);

        ObjectNode query = Json.newObject();
        query.put(STATUS, READY.toString());
        SearchResult result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertTrue("result should be empty", result.getEntries().isEmpty());

        query.put(STATUS, TO_INDEX.toString());
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertEquals("should have to entries to index", 2, result.getEntries().size());

        createIndex(indexMechanism, entry1, fileSystem);
        indexMechanism.commit();
        entry1.setStatus(READY);
        entry1.save();

        query.put(STATUS, READY.toString());
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
        assertEquals("should be entry1", entry1, result.getEntries().get(0));

        query.put(STATUS, TO_INDEX.toString());
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 1, result.getEntries().size());
        assertEquals("should be entry2", entry2, result.getEntries().get(0));

        query.put(STATUS, String.valueOf(ALL));
        result = searchMechanism.search(query).toCompletableFuture().get();
        assertNotNull("should have some result", result);
        assertFalse("result not should be empty", result.getEntries().isEmpty());
        assertEquals("should have found 1 entry", 2, result.getEntries().size());
    }
}
