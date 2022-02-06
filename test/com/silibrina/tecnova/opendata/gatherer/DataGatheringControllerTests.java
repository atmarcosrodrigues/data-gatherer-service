package com.silibrina.tecnova.opendata.gatherer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.coherence.messenger.CoherenceMessageConsumer;
import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor;
import com.silibrina.tecnova.commons.messenger.producer.ProducerService;
import com.silibrina.tecnova.commons.messenger.producer.SimpleProducerService;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.Response;
import com.silibrina.tecnova.commons.model.db.MongoDBPersistenceDrive;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.commons.utils.CommonsFileUtils;
import com.silibrina.tecnova.conversion.fs.parser.OriginalParser;
import com.silibrina.tecnova.conversion.messenger.ConversionMessageConsumer;
import com.silibrina.tecnova.opendata.gatherer.searcher.SimpleSearchMechanism;
import com.silibrina.tecnova.opendata.gatherer.storage.DataStorage;
import com.silibrina.tecnova.opendata.gatherer.storage.SimpleDataStorage;
import com.silibrina.tecnova.opendata.gatherer.storage.fs.local.FileSystemController;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.libs.Json;
import play.test.WithApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static com.google.common.io.Closeables.close;
import static com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor.extractAttr;
import static com.silibrina.tecnova.commons.model.file.FileMetadata.*;
import static com.silibrina.tecnova.commons.model.file.FileVersion.*;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_1;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.XLSX_FILE_1;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.XLS_FILE_1;
import static com.silibrina.tecnova.commons.utils.TestsUtils.*;
import static com.silibrina.tecnova.conversion.messenger.ConversionMessageConsumer.CONSUMER_QUEUE;
import static com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult.COUNT;
import static com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult.ENTRIES;
import static com.silibrina.tecnova.opendata.parsers.ParserController.FormatType.JSON;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.getFileAsFilePart;
import static org.junit.Assert.*;

/**
 * Tests that go through play's routes and activates the DataGathering controller
 */
@RunWith(Parameterized.class)
public class DataGatheringControllerTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(DataGatheringControllerTests.class);
    private static final boolean SYNC = true;

    private DataGatheringController controller;
    private CoherenceMessageConsumer coherenceConsumer;
    private ConversionMessageConsumer conversionConsumer;

    private final FileSystem fileSystem;

    public DataGatheringControllerTests(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Before
    public void setUp() throws IOException, TimeoutException, ConfigurationException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        new MongoDBPersistenceDrive(ConfigFactory.load()).getCollection(EntryMetadata.class).drop();

        coherenceConsumer = new CoherenceMessageConsumer();
        coherenceConsumer.start();

        conversionConsumer = new ConversionMessageConsumer(fileSystem);
        conversionConsumer.start();

        controller = createDataGathererController();
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    private DataGatheringController createDataGathererController() throws IOException, TimeoutException {
        ProducerService messenger = new SimpleProducerService(CONSUMER_QUEUE, SYNC);
        DataStorage storage = new SimpleDataStorage(fileSystem, messenger);
        SimpleSearchMechanism searchMechanism = new SimpleSearchMechanism();
        return new DataGatheringController(messenger, storage, searchMechanism, SYNC);
    }

    @After
    public void cleanUp() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        try {
            JsonNode entities = waitAndGet(controller.list(listAllQuery())).get(ENTRIES);
            for (int i = 0; i < entities.size(); i++) {
                JsonNode node = entities.get(i);
                waitAndGet(controller.delete(node.get("_id").asText()));
            }
        } finally {
            close(controller, true);
            close(coherenceConsumer, true);
            close(conversionConsumer, true);
        }


        new MongoDBPersistenceDrive(ConfigFactory.load()).getCollection(EntryMetadata.class).drop();
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @Parameterized.Parameters
    public static FileSystem[] fileSystems() throws ConfigurationException {
        return getFileSystemsToTest();
    }

    @Test
    public void createTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(123, 0, 0);
        JsonNode js = waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        assertNotNull("should have gotten some id", js.get("_id"));
        assertEntryBody(body, js);
    }

    private JsonNode waitAndGet(CompletionStage<Response<?>> response) throws Throwable {
        Response<?> result = response.toCompletableFuture().get();
        if (result.getPayload() instanceof Throwable) {
            throw ((Throwable) result.getPayload());
        }
        return ((JsonNode) result.getPayload());
    }

    @Test(expected = InvalidConditionException.class)
    public void createWithMissingStartDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.remove("period_start");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = ParseException.class)
    public void createWithInvalidStartDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.put("period_start", "something");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = InvalidConditionException.class)
    public void createWithMissingEndDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.remove("period_end");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = ParseException.class)
    public void createWithInvalidEndDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.put("period_end", "something");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = InvalidConditionException.class)
    public void createWithMissingAuthor() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.remove("author");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = InvalidConditionException.class)
    public void createWithMissingOrg() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.remove("org");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = InvalidConditionException.class)
    public void createWithMissingUploader() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.remove("uploader");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = InvalidConditionException.class)
    public void createWithMissingTitle() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        body.remove("title");
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = InvalidConditionException.class)
    public void createNullBody() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.create(null, getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test
    public void testUpdateFullBody() throws IOException, ParseException, InterruptedException, ExecutionException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        JsonNode js = (JsonNode) controller.create(body, getFileAsFilePart(XLSX_FILE_1.path))
                .toCompletableFuture().get().getPayload();
        assertNotNull("should have gotten some id", js.get("_id"));
        assertEntryBody(body, js);
        assertNotNull("should have not updated file metadata", js.get("file_metadata"));

        JsonNode entry1Body = new EntryMetadata().find(js.get("_id").asText()).toJson();
        JsonNode fileMetadata1 = entry1Body.get("file_metadata");

        body = generateBody(321, -1, 1);
        js = (JsonNode) controller.update(js.get("_id").asText(), body, null)
                .toCompletableFuture().get().getPayload();
        assertNotNull("should have gotten some id", js.get("_id"));
        assertEntryBody(body, js);
        assertNotNull("should have not updated file metadata", js.get("file_metadata"));

        JsonNode fileMetadata2 = js.get("file_metadata");
        assertEquals("should have not updated file metadata", fileMetadata1, fileMetadata2);
    }

    @Test
    public void updateOneFieldTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        assertFieldUpdate("author", "SomeAuthor");
        assertFieldUpdate("period_start", formatter.format(generateDate(-1)));
        assertFieldUpdate("period_end", formatter.format(generateDate(1)));
        assertFieldUpdate("org", "SomeOrg");
        assertFieldUpdate("uploader", "SomeUploader");
        assertFieldUpdate("title", "SomeTitle");
    }

    @Test(expected = InvalidConditionException.class)
    public void updateInvalidAuthor() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertFieldUpdate("author", "  ");
    }

    @Test(expected = ParseException.class)
    public void updateInvalidStartDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertFieldUpdate("period_start", "  ");
    }

    @Test(expected = ParseException.class)
    public void updateInvalidEndDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertFieldUpdate("period_end", "  ");
    }

    @Test(expected = InvalidConditionException.class)
    public void updateInvalidOrg() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertFieldUpdate("org", "  ");
    }

    @Test(expected = InvalidConditionException.class)
    public void updateInvalidUploader() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertFieldUpdate("uploader", "  ");
    }

    @Test(expected = InvalidConditionException.class)
    public void updateInvalidTitle() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertFieldUpdate("title", "  ");
    }

    @Test
    public void testDelete() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(123, 0, 0);

        JsonNode js = waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        assertNotNull("should have gotten some id", js.get("_id"));

        EntryMetadata entry = new EntryMetadata().find(js.get("_id").asText());
        URI path = entry.getFileMetadata().getVersion(OriginalParser.TYPE).getPath();
        assertTrue("should have created the file", uriExists(path));

        assertEntryBody(body, js);

        JsonNode result = waitAndGet(controller.delete(js.get("_id").asText()));
        assertEquals("should have deleted with success message", "Entry deleted [" + js.get("_id").asText() + "]", result.asText());
        assertFalse("should have deleted the file", uriExists(path));
    }

    @Test(expected = InvalidConditionException.class)
    public void nonExistentEntryDelete() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.delete("577bacbdfcca8177baa8e806")); // non-existent-id
    }

    @Test(expected = InvalidConditionException.class)
    public void deleteInvalidId() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.delete("something"));
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = InvalidConditionException.class)
    public void deleteNullId() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.delete(null));
    }

    @Test
    public void listEmptyTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode result = waitAndGet(controller.list(listAllQuery())).get(ENTRIES);
        assertTrue("should have returned an array", result.isArray());

        ArrayNode entries = (ArrayNode) result;
        assertEquals("should have no entries", 0, entries.size());
    }

    @Test
    public void listTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(123, 0, 0);
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        JsonNode result = waitAndGet(controller.list(listAllQuery())).get(ENTRIES);
        assertTrue("should have returned an array", result.isArray());

        ArrayNode entries = (ArrayNode) result;
        assertEquals("should have one entry - 1", 1, entries.size());

        body = generateBody(2, 0, 0);
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        body.setAll(listAllQuery());
        result = waitAndGet(controller.list(body)).get(ENTRIES);
        assertTrue("should have returned an array", result.isArray());

        entries = (ArrayNode) result;
        assertEquals("should have one entry - 1", 1, entries.size());

        result = waitAndGet(controller.list(listAllQuery())).get(ENTRIES);
        assertTrue("should have returned an array", result.isArray());

        entries = (ArrayNode) result;
        assertEquals("should have one entry", 2, entries.size());
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = InvalidConditionException.class)
    public void listNullBody() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.list(null));
    }

    @Test
    public void testListByDateEmpty() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(123, 0, 5);
        waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        ObjectNode query = listAllQuery();
        query.put(EntryMetadata.PERIOD_START, "11-12-2000");
        query.put(EntryMetadata.PERIOD_END, "10-12-2000");
        JsonNode result = waitAndGet(controller.list(query)).get(ENTRIES);

        assertTrue("should have returned an array", result.isArray());

        ArrayNode entries = (ArrayNode) result;
        assertEquals("should have no entries", 0, entries.size());
    }

    @Test
    public void testListByDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body1 = generateBody(1, 0, 5);
        ObjectNode result1 = (ObjectNode) waitAndGet(controller.create(body1, getFileAsFilePart(PDF_FILE_1.path)));

        JsonNode body2 = generateBody(2, -2, 1);
        ObjectNode result2 = (ObjectNode) waitAndGet(controller.create(body2, getFileAsFilePart(PDF_FILE_1.path)));

        JsonNode body3 = generateBody(3, -5, -1);
        ObjectNode result3 = (ObjectNode) waitAndGet(controller.create(body3, getFileAsFilePart(PDF_FILE_1.path)));

        assertEquals("should have one entry", 3, new EntryMetadata().find().size());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        ObjectNode query = listAllQuery();
        query.put(EntryMetadata.PERIOD_START, formatter.format(generateDate(-5)));
        query.put(EntryMetadata.PERIOD_END, formatter.format(generateDate(1)));
        JsonNode resultDate = waitAndGet(controller.list(query)).get(ENTRIES);

        assertTrue("should have returned an array", resultDate.isArray());
        ArrayNode entriesDate = (ArrayNode) resultDate;
        assertEquals("should have found 2 entries", 2, entriesDate.size());

        JsonNode entry1 = new EntryMetadata().find(result1.get("_id").asText()).toJson();
        JsonNode entry2 = new EntryMetadata().find(result2.get("_id").asText()).toJson();
        JsonNode entry3 = new EntryMetadata().find(result3.get("_id").asText()).toJson();

        assertTrue("should be entry 2", entriesDate.get(0).equals(entry2) || entriesDate.get(0).equals(entry2));
        assertTrue("should be entry 3", entriesDate.get(1).equals(entry3) || entriesDate.get(1).equals(entry3));

        query.put(EntryMetadata.PERIOD_START, formatter.format(generateDate(-1)));
        query.put(EntryMetadata.PERIOD_END, formatter.format(generateDate(5)));

        JsonNode resultDate2 = waitAndGet(controller.list(query)).get(ENTRIES);

        assertTrue("should have returned an array", resultDate2.isArray());
        ArrayNode entriesDate2 = (ArrayNode) resultDate2;
        assertEquals("should have no entries", 1, entriesDate2.size());
        assertTrue("should be entry1", entriesDate2.get(0).equals(entry1));
    }

    @Test
    public void listByDateNullStartDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body1 = generateBody(1, 0, 5);
        waitAndGet(controller.create(body1, getFileAsFilePart(PDF_FILE_1.path)));

        JsonNode body2 = generateBody(2, -2, 1);
        ObjectNode result2 = (ObjectNode) waitAndGet(controller.create(body2, getFileAsFilePart(PDF_FILE_1.path)));

        JsonNode body3 = generateBody(3, -5, -1);
        ObjectNode result3 = (ObjectNode) waitAndGet(controller.create(body3, getFileAsFilePart(PDF_FILE_1.path)));

        assertEquals("should have one entry", 3, new EntryMetadata().find().size());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        ObjectNode query = listAllQuery();
        query.put(EntryMetadata.PERIOD_END, formatter.format(generateDate(1)));
        JsonNode result = waitAndGet(controller.list(query)).get(ENTRIES);

        assertNotNull("result should be not null", result);
        assertTrue("should have returned an array", result.isArray());
        ArrayNode entriesDate = (ArrayNode) result;

        assertEquals("should have no entries", 2, entriesDate.size());

        JsonNode entry2 = new EntryMetadata().find(result2.get("_id").asText()).toJson();
        JsonNode entry3 = new EntryMetadata().find(result3.get("_id").asText()).toJson();

        assertTrue("should have entry2", entriesDate.get(0).equals(entry2) || entriesDate.get(1).equals(entry2) );
        assertTrue("should have entry3", entriesDate.get(0).equals(entry3) || entriesDate.get(1).equals(entry3) );
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void listByDateNullEndDate() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body1 = generateBody(1, 0, 5);
        ObjectNode result1 = (ObjectNode) waitAndGet(controller.create(body1, getFileAsFilePart(PDF_FILE_1.path)));

        JsonNode body2 = generateBody(2, -2, 1);
        ObjectNode result2 = (ObjectNode) waitAndGet(controller.create(body2, getFileAsFilePart(PDF_FILE_1.path)));

        JsonNode body3 = generateBody(3, -5, -1);
        waitAndGet(controller.create(body3, getFileAsFilePart(PDF_FILE_1.path)));

        assertEquals("should have one entry", 3, new EntryMetadata().find().size());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        ObjectNode query = listAllQuery();
        query.put(EntryMetadata.PERIOD_START, formatter.format(generateDate(-2)));
        JsonNode result = waitAndGet(controller.list(query)).get(ENTRIES);

        assertNotNull("result should be not null", result);
        assertTrue("should have returned an array", result.isArray());
        ArrayNode entriesDate = (ArrayNode) result;
        assertEquals("should have no entries", 2, entriesDate.size());

        JsonNode entry1 = new EntryMetadata().find(result1.get("_id").asText()).toJson();
        JsonNode entry2 = new EntryMetadata().find(result2.get("_id").asText()).toJson();

        assertTrue("should have entry1", entriesDate.get(0).equals(entry1) || entriesDate.get(1).equals(entry1) );
        assertTrue("should have entry2", entriesDate.get(0).equals(entry2) || entriesDate.get(1).equals(entry2) );
    }

    @Test
    public void testShow() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(1, 0, 5);
        JsonNode createResult = waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        JsonNode showResult = waitAndGet(controller.show(createResult.findValue("_id").asText()));
        assertNotNull("should have found a valid entry", showResult);
        assertFalse("should have a valid json result", showResult.isNull());
        assertEntryBody(createResult, showResult);
        assertFileMeta(createResult.get("file_metadata"), showResult.get("file_metadata"));
    }

    @Test(expected = InvalidConditionException.class)
    public void showInvalidIdTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.show("something"));
    }

    @Test(expected = InvalidConditionException.class)
    public void showNonExistentTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.show("577bacbdfcca8177baa8e806")); // non-existent id
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = InvalidConditionException.class)
    public void showNullId() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.show(null));
    }

    @Test
    public void uploadFileTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(1, 0, 1);
        JsonNode resultCreate = waitAndGet(controller.create(body, null));

        assertNotNull("should have a valid result", resultCreate);
        assertFalse("should have a non null result", resultCreate.isNull());
        assertEntryBody(body, resultCreate);
        assertTrue("should have file_metadata field", resultCreate.has("file_metadata"));
        assertTrue("should have file_metadata field null", resultCreate.get("file_metadata").isNull());

        JsonNode resultUploadFile = waitAndGet(controller.upload(resultCreate.get("_id").asText(),
                getFileAsFilePart(XLS_FILE_1.path)));
        assertNotNull("should have a valid result", resultUploadFile);
        assertFalse("should have a non null result", resultUploadFile.isNull());
        assertEntryBody(resultCreate, resultUploadFile);
        assertTrue("should have file_metadata field", resultUploadFile.has("file_metadata"));
        assertFalse("should not file_metadata field null", resultUploadFile.get("file_metadata").isNull());

        JsonNode fileMetadata = resultUploadFile.get("file_metadata");

        assertNotNull("should have an original name", fileMetadata.get(ORIGINAL_NAME));
        assertNotNull("should have a name", fileMetadata.get(NAME));
        assertNotNull("should have a name", fileMetadata.get(COUNT));
        assertNotNull("should have versions", fileMetadata.get(VERSIONS));

        assertTrue("versions should be an array", fileMetadata.get(VERSIONS).isArray());
        ArrayNode versions = (ArrayNode) fileMetadata.get(VERSIONS);
        for (int i = 0; i < versions.size(); i++) {
            JsonNode version = versions.get(i);
            logger.debug("version: {}", version);
        }

        assertEquals("versions should have 5 elements", 5, versions.size());

        for (int i = 0; i < versions.size(); i++) {
            JsonNode version = versions.get(i);
            assertNotNull("should have an url", version.get(URL));
            assertNotNull("should have a mime type", version.get(MIME_TYPE));
            assertNotNull("should have a size", version.get(SIZE));
            assertNotNull("should have a subtype", version.get(MIME_SUBTYPE));
            assertNotNull("should have a type", version.get(TYPE));
        }

        String id = resultUploadFile.get("_id").asText();
        EntryMetadata entry = new EntryMetadata().find(id);

        FileVersion originalVersion = entry.getFileMetadata().getVersion(OriginalParser.TYPE);
        assertTrue("file should exist", uriExists(originalVersion.getPath()));
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = InvalidConditionException.class)
    public void uploadNullFile() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(1, 0, 1);
        JsonNode resultCreate = waitAndGet(controller.create(body, null));
        assertNotNull("should have a valid result", resultCreate);
        assertFalse("should have a non null result", resultCreate.isNull());
        assertEntryBody(body, resultCreate);
        assertTrue("should have file_metadata field", resultCreate.has("file_metadata"));
        assertTrue("should have file_metadata field null", resultCreate.get("file_metadata").isNull());

        waitAndGet(controller.upload(resultCreate.get("_id").asText(), null));
    }

    @Test(expected = InvalidConditionException.class)
    public void uploadFileNonExistentIdTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.upload("577bacbdfcca8177baa8e806", getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test(expected = InvalidConditionException.class)
    public void uploadFileInvalidIdTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        waitAndGet(controller.upload("something", getFileAsFilePart(XLSX_FILE_1.path)));
    }

    @Test
    public void downloadFileTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        String originalPath = XLSX_FILE_1.path;
        String updatedPath = XLS_FILE_1.path;

        File originalFile = new File(ODFileUtils.getCurrentDir(originalPath));
        File updatedFile = new File(ODFileUtils.getCurrentDir(updatedPath));
        assertFalse("content of the submitted file must be different from the updated",
                FileUtils.contentEquals(originalFile, updatedFile));

        JsonNode body = generateBody(1, 0, 1);
        JsonNode createResult = waitAndGet(controller.create(body, getFileAsFilePart(originalPath)));

        assertNotNull("must have a result", createResult);
        assertFalse("must have a valid result", createResult.isNull());
        assertEntryBody(body, createResult);

        EntryMetadata entryResult = new EntryMetadata().find(createResult.get("_id").asText());
        FileVersion originalVersion = entryResult.getFileMetadata().getVersion(OriginalParser.TYPE);

        InputStreamAttrExtractor.InputStreamAttr originalFileAttr = extractAttr(originalFile);
        InputStreamAttrExtractor.InputStreamAttr updatedFileAttr = extractAttr(updatedFile);

        assertEquals("content of the submitted file must be the same of the original",
                originalFileAttr.checksum, originalVersion.getHash());
        assertNotEquals("content of the submitted file must be different from the updated",
                updatedFileAttr.checksum, originalVersion.getHash());

        JsonNode createUploadFile = waitAndGet(controller.upload(createResult.get("_id").asText(),
                getFileAsFilePart(updatedPath)));

        assertNotNull("must have a result", createResult);
        assertFalse("must have a valid result", createResult.isNull());
        assertEntryBody(createResult, createUploadFile);

        entryResult = new EntryMetadata().find(createResult.get("_id").asText());
        originalVersion = entryResult.getFileMetadata().getVersion(OriginalParser.TYPE);

        assertNotEquals("content of the submitted file must be the same of the original",
                extractAttr(originalFile).checksum, originalVersion.getHash());
        assertEquals("content of the submitted file must be different from the updated",
                extractAttr(updatedFile).checksum, originalVersion.getHash());
    }

    @Test
    public void parseFileTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body1 = generateBody(1, 0, 1);
        JsonNode createResult1 = waitAndGet(controller.create(body1, getFileAsFilePart(XLS_FILE_1.path)));

        assertNotNull("must have a result", createResult1);
        assertFalse("must have a valid result", createResult1.isNull());
        assertEntryBody(body1, createResult1);

        JsonNode body2 = generateBody(2, 0, 1);
        JsonNode createResult2 = waitAndGet(controller.create(body2, getFileAsFilePart(XLSX_FILE_1.path)));

        assertNotNull("must have a result", createResult2);
        assertFalse("must have a valid result", createResult2.isNull());
        assertEntryBody(body2, createResult2);

        try (InputStream xlsFileParsedStream = getInputStream(createResult1, String.valueOf(JSON).toLowerCase());
            InputStream xlsxFileParsedStream = getInputStream(createResult2, String.valueOf(JSON).toLowerCase())) {

            JsonNode content = new ObjectMapper().readTree(xlsFileParsedStream);
            JsonNode content2 = new ObjectMapper().readTree(xlsxFileParsedStream);

            assertEquals("should be a .xls file", "EXCEL97", content.get("version").asText());
            assertEquals("should be a .xlsx file", "EXCEL2007", content2.get("version").asText());
        }
    }

    private InputStream getInputStream(JsonNode body, String type) throws IOException {
        String entryId = body.get("_id").asText();
        EntryMetadata entry = new EntryMetadata().find(entryId);
        FileVersion version = entry.getFileMetadata().getVersion(type.toLowerCase());
        return CommonsFileUtils.getInputStream(version.getPath());
    }

    @SuppressWarnings("ConstantConditions")
    @Test(expected = InvalidConditionException.class)
    public void downloadFileNullIdTest() throws Throwable {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = Json.newObject();
        Response<?> response = new FileSystemController().get(OriginalParser.TYPE, null, body).toCompletableFuture().get();
        if (response.isException()) {
            throw (Throwable) response.getPayload();
        }
    }

    private void assertFileMeta(JsonNode expected, JsonNode actual) {
        assertEquals("should be the same original_name", expected.get(ORIGINAL_NAME), actual.get(ORIGINAL_NAME));
        assertEquals("should be the same name", expected.get(NAME), actual.get(NAME));
        assertEquals("should be the same name", expected.get(COUNT), actual.get(COUNT));
        assertEquals("should be the same name", expected.get(VERSIONS).size(), actual.get(VERSIONS).size());
    }

    private void assertFieldUpdate(String field, String newValue) throws Throwable {
        ObjectNode body = generateBody(123, 0, 0);
        JsonNode createResult = waitAndGet(controller.create(body, getFileAsFilePart(XLSX_FILE_1.path)));

        assertEquals("should have updated " + field, body.get(field).asText(), createResult.get(field).asText());
        assertEntryBody(body, createResult);

        ObjectNode newBody = Json.newObject();
        newBody.put(field, newValue);
        body.put(field, newValue);
        JsonNode updateResult = waitAndGet(controller.update(createResult.get("_id").asText(), newBody, null));

        assertEntryBody(body, updateResult);
        JsonNode showResult = waitAndGet(controller.show(createResult.get("_id").asText()));

        assertEntryBody(showResult, updateResult);
        assertFileMeta(createResult.get("file_metadata"), updateResult.get("file_metadata"));
        assertFileMeta(showResult.get("file_metadata"), updateResult.get("file_metadata"));
    }
}
