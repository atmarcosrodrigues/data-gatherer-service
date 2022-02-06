package com.silibrina.tecnova.opendata.gatherer.searcher;

import com.silibrina.tecnova.coherence.indexer.LuceneIndexer;
import com.silibrina.tecnova.coherence.utils.CoherenceTestsUtils;
import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.test.WithApplication;

import java.util.Set;

import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_1;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_2;
import static com.silibrina.tecnova.commons.utils.TestsUtils.cleanUpEntries;
import static com.silibrina.tecnova.commons.utils.TestsUtils.generateEntryMetadata;
import static com.silibrina.tecnova.commons.utils.TestsUtils.getFileSystemsToTest;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class LuceneSearcherTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(LuceneSearcherTests.class);

    private String indexDir;
    private LuceneIndexer indexer;
    private LuceneSearcher searcher;
    private final FileSystem fileSystem;

    public LuceneSearcherTests(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Before
    public void setUp() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        cleanUpEntries();

        indexDir = CoherenceTestsUtils.getIndexDir();
        indexer = new LuceneIndexer(indexDir);

        EntryMetadata entry1 = generateEntryMetadata(3, 0, 0, PDF_FILE_1, fileSystem);
        createIndex(indexer, entry1, fileSystem);
        indexer.commit();
        assertEquals("should have one indexed file", 1, indexer.size());

        searcher = new LuceneSearcher(indexDir);
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        indexer.close();

        cleanUpEntries();
    }

    @Parameterized.Parameters
    public static FileSystem[] fileSystems() throws ConfigurationException {
        return getFileSystemsToTest();
    }

    @Test
    public void searchEntry() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);

        createIndex(indexer, entry2, fileSystem);
        indexer.commit();

        assertEquals("should have one indexed file", 2, indexer.size());

        Set<String> ids = new LuceneSearcher(indexDir).search("octothorpe");
        assertNotNull("result can not be null", ids);
        assertEquals("should have found exactly one file", 1, ids.size());

        EntryMetadata foundEntry = new EntryMetadata().find(ids.iterator().next());
        assertTrue("should have found entry2", entry2.equals(foundEntry));
    }

    @Test(expected = InvalidConditionException.class)
    public void searchEntryNull() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        searcher.search(null);
    }

    @Test(expected = InvalidConditionException.class)
    public void searchEntryEmpty() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        searcher.search("");
    }

    @Test
    public void searchPhrase() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexer, entry2, fileSystem);
        indexer.commit();
        assertEquals("should have one indexed file", 2, indexer.size());

        LuceneSearcher searcher = new LuceneSearcher(indexDir);
        Set<String> ids = searcher.search("weird octothorpe");
        assertNotNull("result can not be null", ids);
        assertEquals("should have found exactly one file", 1, ids.size());

        EntryMetadata foundEntry = new EntryMetadata().find(ids.iterator().next());
        assertTrue("should have found entry2", entry2.equals(foundEntry));
    }

    @Test
    public void searchInTitle() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        EntryMetadata entry2 = generateEntryMetadata(4, 0, 0, PDF_FILE_2, fileSystem);
        createIndex(indexer, entry2, fileSystem);
        indexer.commit();
        assertEquals("should have one indexed file", 2, indexer.size());

        LuceneSearcher searcher = new LuceneSearcher(indexDir);
        Set<String> ids = searcher.search(entry2.getAuthor());
        assertNotNull("result can not be null", ids);
        assertEquals("should have found exactly one file", 1, ids.size());

        EntryMetadata foundEntry = new EntryMetadata().find(ids.iterator().next());
        assertTrue("should have found entry2", entry2.equals(foundEntry));
    }

}

