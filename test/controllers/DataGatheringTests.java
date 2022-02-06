package controllers;

import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.coherence.CoherenceMain;
import com.silibrina.tecnova.commons.exceptions.InvalidConfigurationException;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor;
import com.silibrina.tecnova.commons.fs.local.LocalFileSystem;
import com.silibrina.tecnova.commons.fs.swift.SwiftFileSystem;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.file.FileMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.commons.utils.CommonsFileUtils;
import com.silibrina.tecnova.conversion.ConversionMain;
import com.silibrina.tecnova.conversion.fs.SupportedTypes.InputFormats;
import com.silibrina.tecnova.conversion.fs.parser.JsonParser;
import com.silibrina.tecnova.conversion.fs.parser.OriginalParser;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult;
import com.silibrina.tecnova.opendata.utils.HeaderWrapper;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.configuration.ConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import play.Logger;
import play.core.j.JavaResultExtractor;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.DataPart;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.WithApplication;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static com.silibrina.tecnova.commons.conf.ConfigConstants.Strings.GATHERER_FS;
import static com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor.extractAttr;
import static com.silibrina.tecnova.commons.model.file.FileMetadata.*;
import static com.silibrina.tecnova.commons.model.file.FileVersion.*;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getCurrentDir;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getInputStream;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_1;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_2;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.XLS_FILE_1;
import static com.silibrina.tecnova.commons.utils.TestsUtils.*;
import static com.silibrina.tecnova.conversion.fs.SupportedTypes.InputFormats.PDF;
import static com.silibrina.tecnova.conversion.fs.SupportedTypes.InputFormats.XLS;
import static com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchResult.*;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static controllers.DataGatherer.BODY;
import static controllers.DataGatherer.DATAFILE;
import static org.junit.Assert.*;
import static play.test.Helpers.*;

@RunWith(Parameterized.class)
public class DataGatheringTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(DataGatheringTests.class);

    private String entryId = null;
    private CoherenceMain coherenceMain;
    private ConversionMain conversionMain;

    private final FileSystem fileSystem;

    public DataGatheringTests(FileSystem fileSystem) throws IOException {
        this.fileSystem = fileSystem;

        setEnvVars(fileSystem);
        ConfigFactory.invalidateCaches();
    }

    private void setEnvVars(FileSystem fileSystem) throws IOException {
        System.setProperty(GATHERER_FS.field, getFileSystemConf(fileSystem));
    }

    private String getFileSystemConf(FileSystem fileSystem) {
        if (fileSystem instanceof LocalFileSystem) {
            return "local";
        } else if (fileSystem instanceof SwiftFileSystem) {
            return "swift";
        } else {
            throw new InvalidConfigurationException("invalid file system instance");
        }
    }

    @Before
    public void setUp() throws IOException, TimeoutException, ConfigurationException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        coherenceMain = new CoherenceMain(false);
        coherenceMain.start();

        conversionMain = new ConversionMain(false, fileSystem);
        conversionMain.start();

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        JsonNode body = generateBody(1, 0, 1);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        entryId = resultJson.findValue("_id").asText();

        request = new RequestBuilder().method(POST).path("/data/upload/" + entryId);
        request.bodyMultipart(getFileAsMultipartFormData(XLS_FILE_1.path), mat);

        result = route(request);
        assertResultIsOk(result);

        resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "File uploaded");
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws IOException, InterruptedException, ConfigurationException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        try {
            deleteEntries();
            cleanUpEntries();
        } finally {
            coherenceMain.stopCoherence();
            conversionMain.stopConverter();
        }

        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @Parameterized.Parameters
    public static FileSystem[] fileSystems() throws ConfigurationException {
        return getFileSystemsToTest();
    }

    private void deleteEntries() throws IOException {
        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?" + listAllQueryParams());
        Result result = route(request);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        assertNotNull("should have a data entry", resultJson.get("data"));

        JsonNode data = resultJson.get("data");
        assertNotNull("should have a count entry", data.get(SearchResult.COUNT));
        assertNotNull("should have an entry with entries",data.get(ENTRIES));

        JsonNode entries = data.get(ENTRIES);

        assertTrue("list result must be an array", entries.isArray());
        ArrayNode entriesJson = (ArrayNode) entries;
        for (int i = 0; i < entriesJson.size(); i++) {
            JsonNode node = entriesJson.get(i);
            request = new RequestBuilder().method(DELETE).path("/data/" + node.get("_id").asText());
            assertResultIsOk(route(request));
        }
    }

    @Test
    public void createTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode body = generateBody(2, 0, 1);
        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");
        assertEntryBody(body, resultJson.get("data"));
    }

    @Test
    public void createWithMissingStartDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.remove("period_start");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Start period can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithInvalidStartDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.put("period_start", "something");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Unparseable date: \"something\"");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithMissingEndDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.remove("period_end");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "End period can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithInvalidEndDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.put("period_end", "something");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Unparseable date: \"something\"");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithMissingAuthorTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.remove("author");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Author can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithMissingOrgTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.remove("org");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Org can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithMissingUploaderTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.remove("uploader");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Uploader can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithMissingTitleTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 0, 1);
        body.remove("title");

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Title can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createWithStartGreaterThanEndTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(2, 1, 0);

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Start date can not be after end date");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void createQueryParamsTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        String uri = "/data" + generateBodyUriString(3, 0, 1);

        Http.RequestBuilder request = new Http.RequestBuilder().method(POST)
                .uri(uri);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");

        JsonNode expectedBody = generateBody(3, 0, 1);
        assertEntryBody(expectedBody, resultJson.get("data"));
    }

    @Test
    public void createFormWithFileTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        String uri = "/data" + generateBodyUriString(4, 0, 1);
        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).uri(uri);
        request.bodyMultipart(generateFormBody(4, 0, 1, XLS_FILE_1.path), mat);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");

        JsonNode expectedBody = generateBody(4, 0, 1);
        JsonNode dataJson = resultJson.get("data");

        assertEntryBody(expectedBody, dataJson);
        assertTrue("should have file_metadata field", dataJson.has("file_metadata"));
        assertFalse("should not file_metadata field null", dataJson.get("file_metadata").isNull());

        assertFileMetadata(dataJson, XLS);
    }

    @Test
    public void uploadTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(POST).path("/data/upload/" + entryId);
        request.bodyMultipart(getFileAsMultipartFormData(XLS_FILE_1.path), mat);

        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "File uploaded");

        JsonNode dataJson = resultJson.get("data");
        assertTrue("should have file_metadata field", dataJson.has("file_metadata"));
        assertFalse("should not file_metadata field null", dataJson.get("file_metadata").isNull());

        assertFileMetadata(dataJson, XLS);
    }

    private void assertFileMetadata(JsonNode dataJson, InputFormats inputFormats) {
        JsonNode fileMetadata = dataJson.get("file_metadata");
        assertNotNull("should have an original name", fileMetadata.get(ORIGINAL_NAME));
        assertNotNull("should have a name", fileMetadata.get(NAME));
        assertNotNull("should have a name", fileMetadata.get(FileMetadata.COUNT));
        assertNotNull("should have versions", fileMetadata.get(VERSIONS));
        assertTrue("versions should be an array", fileMetadata.get(VERSIONS).isArray());
        ArrayNode versions = (ArrayNode) fileMetadata.get(VERSIONS);
        assertEquals("versions should have " + inputFormats.outputFormats.length + " elements",
                inputFormats.outputFormats.length, versions.size());

        for (int i = 0; i < versions.size(); i++) {
            JsonNode version = versions.get(i);
            assertNotNull("should have an url", version.get(URL));
            assertNotNull("should have a mime type", version.get(MIME_TYPE));
            assertNotNull("should have a size", version.get(SIZE));
            assertNotNull("should have a subtype", version.get(MIME_SUBTYPE));
            assertNotNull("should have a type", version.get(TYPE));
        }

        String id = dataJson.get("_id").asText();
        EntryMetadata entry = new EntryMetadata().find(id);

        assertTrue("file should exist", uriExists(entry.getFileMetadata()
                .getVersion(OriginalParser.TYPE).getPath()));
    }

    @Test
    public void uploadNonExistentIdTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(POST).path("/data/upload/5783ae05fcca814752ba5ea0"); // non-existent id
        request.bodyMultipart(getFileAsMultipartFormData(XLS_FILE_1.path), mat);

        Result result = route(request);

        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Entry not found [5783ae05fcca814752ba5ea0]");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void uploadInvalidIdTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(POST).path("/data/upload/something");
        request.bodyMultipart(getFileAsMultipartFormData(XLS_FILE_1.path), mat);

        Result result = route(request);

        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "invalid hexadecimal representation of an ObjectId: [something]");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void uploadMissingFileTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(POST).path("/data/upload/" + entryId);
        Result result = route(request);
        assertResultIsError(result);
        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Missing file");
    }

    @Test
    public void updateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        ObjectNode body = generateBody(5, 0, 1);

        RequestBuilder request = new RequestBuilder().method(PUT).path("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry updated");
        assertEntryBody(body, resultJson.get("data"));
    }

    @Test
    public void updateWithInvalidStartDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = Json.newObject();
        body.put("period_start", "something");

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Unparseable date: \"something\"");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithInvalidEndDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = Json.newObject();
        body.put("period_end", "something");

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Unparseable date: \"something\"");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithInvalidOrgTest() throws IOException {
        logger.debug("==> testing...");

        JsonNode before = getEntry(entryId);

        ObjectNode body = Json.newObject();
        body.put("org", " ");

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Org can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithInvalidUploaderTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = Json.newObject();
        body.put("uploader", " ");

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Uploader can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithInvalidTitleTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = Json.newObject();
        body.put("title", " ");

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Title can not be null");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithNonExistentIdTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = generateBody(6, 0, 1);

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/5783ae05fcca814752ba5ea0");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Entry not found [5783ae05fcca814752ba5ea0]");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithInvalidIdTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = generateBody(5, 0, 1);

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/something");
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "invalid hexadecimal representation of an ObjectId: [something]");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        JsonNode after = getEntry(entryId);
        assertEntryBody(before.get("data"), after.get("data"));
    }

    @Test
    public void updateWithEmptyBodyTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode before = getEntry(entryId);

        ObjectNode body = Json.newObject();

        RequestBuilder request = new RequestBuilder().method(PUT).uri("/data/" + entryId);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry updated");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());

        assertEntryBody(before.get("data"), resultJson.get("data"));
    }

    @Test
    public void updateFormWithFileTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        String uri = "/data/" + entryId + generateBodyUriString(5, 0, 1);
        Http.RequestBuilder request = new Http.RequestBuilder().method(PUT).uri(uri);
        request.bodyMultipart(generateFormBody(5, 0, 1, XLS_FILE_1.path), mat);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry updated");

        JsonNode expectedBody = generateBody(5, 0, 1);
        JsonNode dataJson = resultJson.get("data");

        assertEntryBody(expectedBody, dataJson);
        assertTrue("should have file_metadata field", dataJson.has("file_metadata"));
        assertFalse("should not file_metadata field null", dataJson.get("file_metadata").isNull());

        assertFileMetadata(dataJson, XLS);
    }

    @Test
    public void deleteTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(DELETE).path("/data/" + entryId);
        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry deleted");

        request = new RequestBuilder().method(GET).uri("/data" + "?" + listAllQueryParams());
        result = route(request);
        assertResultIsOk(result);
        resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void nonExistentEntryDeleteTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(DELETE).path("/data/5783ae05fcca814752ba5ea0"); // non-existent entry id
        Result result = route(request);

        assertResultIsError(result);
        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Entry not found [5783ae05fcca814752ba5ea0]");
    }

    @Test
    public void invalidIdEntryDeleteTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(DELETE).path("/data/something"); // invalid entry id
        Result result = route(request);

        assertResultIsError(result);
        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "invalid hexadecimal representation of an ObjectId: [something]");
    }

    @Test
    public void listTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data" + "?" + listAllQueryParams());
        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");
        JsonNode entries = resultJson.get("data").get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 1, entries.size());

        JsonNode entry = getEntry(entryId);
        assertEntryBody(entry.get("data"), entries.get(0));
    }

    @Test
    public void listLikeAuthor() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertListByPartial(EntryMetadata.AUTHOR, "me_auth");
    }

    @Test
    public void listLikeOrg() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertListByPartial(EntryMetadata.ORG, "me_org");
    }

    @Test
    public void listLikeTitle() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertListByPartial(EntryMetadata.TITLE, "me_tit");
    }

    @Test
    public void listLikeUploader() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        assertListByPartial(EntryMetadata.UPLOADER, "me_uploa");
    }

    private void assertListByPartial(String field, String content) throws IOException {
        RequestBuilder request = new RequestBuilder().method(GET).uri(
                "/data"
                        + "?" + field + "=" + content
                        + "&" + EntryMetadata.STATUS + "=" + EntryMetadata.Status.ALL);
        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");
        JsonNode entries = resultJson.get("data").get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 1, entries.size());

        JsonNode entry = getEntry(entryId);
        assertEntryBody(entry.get("data"), entries.get(0));
    }

    @Test
    public void listEmptyTest() throws IOException, InterruptedException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        deleteEntries();

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data");
        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");
        JsonNode entries = resultJson.get("data").get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 0, entries.size());
    }

    @Test
    public void listIntervalByDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        JsonNode entry2 = createEntry(7, -3, -1, XLS_FILE_1.path);
        JsonNode entry4 = createEntry(9, -2, -1, XLS_FILE_1.path);

        String start = formatter.format(generateDate(-3));
        String end = formatter.format(generateDate(-1));

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data"
                + "?" + EntryMetadata.PERIOD_START + "=" + start
                + "&" + EntryMetadata.PERIOD_END + "=" + end
                + "&" + listAllQueryParams());
        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode entries = resultJson.get("data").get(ENTRIES);

        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());

        entry2 = new EntryMetadata().find(entry2.get("_id").asText()).toJson();
        entry4 = new EntryMetadata().find(entry4.get("_id").asText()).toJson();

        assertTrue("should be entry 2 or 3", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(0)).equals(toString(entry4)));
        assertTrue("should be entry 2 or 3", toString(entries.get(1)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry4)));
    }

    @Test
    public void listAllByDateTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        createEntry(7, -3, -1, XLS_FILE_1.path);
        createEntry(8, -2, 0, XLS_FILE_1.path);
        createEntry(9, -2, -1, XLS_FILE_1.path);

        String start = formatter.format(generateDate(-3));
        String end = formatter.format(generateDate(1));

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data"
                + "?" + EntryMetadata.PERIOD_START + "=" + start
                + "&" + EntryMetadata.PERIOD_END + "=" + end
                + "&" + listAllQueryParams());

        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode entries = resultJson.get("data").get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 4, entries.size());
    }

    @Test
    public void listByDateInvalidStartTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        String end = formatter.format(generateDate(1));

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data"
                + "?" + EntryMetadata.PERIOD_START + "=" + "something"
                + "&" + EntryMetadata.PERIOD_END + "=" + end);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Unparseable date: \"something\"");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void listByDateInvalidEndTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        String start = formatter.format(generateDate(-3));

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data"
                + "?" + EntryMetadata.PERIOD_START + "=" + start
                + "&" + EntryMetadata.PERIOD_END + "=" + "something"
                + "&" + listAllQueryParams());
        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Unparseable date: \"something\"");

        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void listByDateAndContent() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        createEntry(7, -3, -1, PDF_FILE_1.path);
        createEntry(8, -2, 0, PDF_FILE_1.path);
        ObjectNode entry1 = (ObjectNode) createEntry(9, -2, -1, PDF_FILE_2.path);
        createEntry(10, -4, 0, PDF_FILE_2.path);

        String start = formatter.format(generateDate(-3));
        String end = formatter.format(generateDate(2));

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data"
                + "?" + EntryMetadata.PERIOD_START + "=" + start
                + "&" + EntryMetadata.PERIOD_END + "=" + end
                + "&" + EntryMetadata.CONTENT + "=octothorpe"
                + "&" + listAllQueryParams());
        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);

        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 1, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry1 = (ObjectNode) new EntryMetadata().find(entry1.get("_id").asText()).toJson();

        assertEquals("should exist only one entry created", toString(entry1), toString(entries.get(0)));
    }

    @Test
    public void listWithJson() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(10, 0, 1, PDF_FILE_1.path);
        ObjectNode entry1 = (ObjectNode) createEntry(11, 1, 2, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 1, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry1 = (ObjectNode) new EntryMetadata().find(entry1.get("_id").asText()).toJson();

        assertEquals("should have found entry1", toString(entry1), toString(entries.get(0)));
    }

    @Test
    public void listWithJsonManyResults() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(10, 0, 1, PDF_FILE_1.path);
        ObjectNode entry1 = (ObjectNode) createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode entry2 = (ObjectNode) createEntry(11, 3, 7, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry1 = (ObjectNode) new EntryMetadata().find(entry1.get("_id").asText()).toJson();
        entry2 = (ObjectNode) new EntryMetadata().find(entry2.get("_id").asText()).toJson();

        assertTrue("should have found entry1", toString(entries.get(0)).equals(toString(entry1))
                || toString(entries.get(1)).equals(toString(entry1)));
        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry2)));
    }

    @Test
    public void listByTitle() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(23423242, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode entry2 = (ObjectNode) createEntry(12, 3, 7, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());


        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry2 = (ObjectNode) new EntryMetadata().find(entry2.get("_id").asText()).toJson();

        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry2)));
    }

    @Test
    public void listByTitleManyResults() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(23423242, 0, 1, PDF_FILE_1.path);
        ObjectNode result1 = (ObjectNode) createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode result2 = (ObjectNode) createEntry(12, 3, 7, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());
        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should have found 2 entries", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        ObjectNode entry1 = (ObjectNode) new EntryMetadata().find(result1.get("_id").asText()).toJson();
        ObjectNode entry2 = (ObjectNode) new EntryMetadata().find(result2.get("_id").asText()).toJson();

        assertTrue("should have found entry1", toString(entries.get(0)).equals(toString(entry1))
                || toString(entries.get(1)).equals(toString(entry1)));
        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry2)));
    }

    @Test
    public void listByOrg() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(23423242, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode entry2 = (ObjectNode) createEntry(12, 3, 7, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry2 = (ObjectNode) new EntryMetadata().find(entry2.get("_id").asText()).toJson();

        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2)) || toString(entries.get(1)).equals(toString(entry2)));
    }

    private String toString(JsonNode node) {
        return String.valueOf(node);
    }

    @Test
    public void listByOrgManyResults() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(23423242, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode entry2 = (ObjectNode) createEntry(12, 3, 7, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry2 = (ObjectNode) new EntryMetadata().find(entry2.get("_id").asText()).toJson();

        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry2)));
    }

    @Test
    public void listByAuthor() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(23423242, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode entry2 = (ObjectNode) createEntry(12, 3, 7, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe"  + "&" + listAllQueryParams());

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry2 = (ObjectNode) new EntryMetadata().find(entry2.get("_id").asText()).toJson();

        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry2)));
    }

    @Test
    public void listByAuthorManyResults() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(23423242, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);
        ObjectNode entry2 = (ObjectNode) createEntry(12, 3, 7, PDF_FILE_2.path);

        logger.debug("preparing get request...");
        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=octothorpe" + "&" + listAllQueryParams());

        logger.debug("preparing to route...");
        Result result = route(request);
        logger.debug("asserting result is Ok...");
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        logger.debug("checking header...");
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        assertNotNull("should have count entry", data.get(SearchResult.COUNT));
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 2, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());

        entry2 = (ObjectNode) new EntryMetadata().find(entry2.get("_id").asText()).toJson();

        assertTrue("should have found entry2", toString(entries.get(0)).equals(toString(entry2))
                || toString(entries.get(1)).equals(toString(entry2)));
    }

    @Test
    public void showTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(GET).path("/data/" + entryId);
        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));

        checkHeaderOk(resultJson, "Show entry");
        JsonNode body = generateBody(1, 0, 1);
        assertEntryBody(body, resultJson.get("data"));
    }

    @Test
    public void showInvalidIdTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(GET).path("/data/something");
        Result result = route(request);

        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "invalid hexadecimal representation of an ObjectId: [something]");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void showNonExistentEntryTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(GET).path("/data/5783e8cbfcca81184b7b57ba");
        Result result = route(request);

        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Entry not found [5783e8cbfcca81184b7b57ba]");
        assertTrue("data response should be empty", resultJson.get("data").asText().isEmpty());
    }

    @Test
    public void contentFileParsedTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).path("/data/upload/" + entryId);
        request.bodyMultipart(getFileAsMultipartFormData(XLS_FILE_1.path), mat);

        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "File uploaded");

        JsonNode entryJson = resultJson.get(HeaderWrapper.DATA);
        JsonNode version = getVersion(entryJson, JsonParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = version.get(FileVersion.URL).asText();

        resultJson = request(url);
        assertTrue("should have some content", resultJson.toString().length() > 4000);
    }

    private JsonNode request(String url) throws IOException {
        URI uri = URI.create(url);

        if (fileSystem instanceof LocalFileSystem) {
            String path = uri.getPath();
            Http.RequestBuilder requestGet = new Http.RequestBuilder().method(GET).uri(path);
            Result resultGet = route(requestGet);
            return (new ObjectMapper()).readTree(contentAsString(resultGet, mat));
        } else if (fileSystem instanceof SwiftFileSystem) {
            try (InputStream inputStream = CommonsFileUtils.getInputStream(uri)) {
                return new ObjectMapper().readTree(inputStream);
            }
        } else {
            throw new IOException("File system not found!");
        }
    }

    @Test
    public void contentFileOriginalTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).path("/data/upload/" + entryId);
        request.bodyMultipart(getFileAsMultipartFormData(XLS_FILE_1.path), mat);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "File uploaded");

        JsonNode entryJson = resultJson.get(HeaderWrapper.DATA);
        JsonNode version = getVersion(entryJson, OriginalParser.TYPE);
        assertNotNull("version should not be null", version);

        InputStreamAttrExtractor.InputStreamAttr downloadedFileAttr = attrFromUrl(version.get(FileVersion.URL).asText());
        InputStreamAttrExtractor.InputStreamAttr originalFileAttr = extractAttr(new File(getCurrentDir(XLS_FILE_1.path)));
        assertEquals("should be the exact same original file", originalFileAttr, downloadedFileAttr);
    }

    private InputStreamAttrExtractor.InputStreamAttr attrFromUrl(String url) throws IOException {
        URI uri = URI.create(url);

        if (fileSystem instanceof LocalFileSystem) {
            String path = uri.getPath();
            Http.RequestBuilder requestGet = new Http.RequestBuilder().method(GET).uri(path);
            Result resultGet = route(requestGet);
            assertNotNull("result should no be null", resultGet);
            assertEquals("the original file is a excel spreadsheet",
                    Optional.of("application/excel"), resultGet.contentType());
            ByteString body = JavaResultExtractor.getBody(resultGet, 100000L, mat);
            assertNotNull("body of the result can not be null", body);

            try (InputStream inputStream = new ByteArrayInputStream(body.toArray())) {
                return extractAttr(inputStream);
            }
        } else if (fileSystem instanceof SwiftFileSystem) {
            try (InputStream inputStream = getInputStream(uri)) {
                return extractAttr(inputStream);
            }
        } else {
            throw new IOException("File system not found!");
        }
    }

    @Test
    public void contentNonExistentFileTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        RequestBuilder request = new RequestBuilder().method(POST).uri("/data");
        JsonNode body = generateBody(5, 0, 1);
        request.bodyJson(body);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        entryId = resultJson.findValue("_id").asText();

        RequestBuilder requestGet = new RequestBuilder().method(GET).uri("/storage/original/" + entryId);
        Result resultGet = route(requestGet);
        assertResultIsError(resultGet);

        resultJson = (new ObjectMapper()).readTree(contentAsString(resultGet));
        checkHeaderError(resultJson, "Entry [" + entryId + "] does not have a file yet.");
    }

    @Test
    public void createEntryUsingMultipartFormData() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).uri("/data");

        request.bodyMultipart(generateFormBody(4, 0, 1, PDF_FILE_2.path), mat);
        Result result = route(request);

        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");

        JsonNode expectedBody = generateBody(4, 0, 1);
        JsonNode dataJson = resultJson.get("data");

        assertEntryBody(expectedBody, dataJson);
        assertTrue("should have file_metadata field", dataJson.has("file_metadata"));
        assertFalse("should not file_metadata field null", dataJson.get("file_metadata").isNull());

        assertFileMetadata(dataJson, PDF);
    }

    @Test
    public void createEntryJsonMultipartFormData() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).uri("/data");

        List<Http.MultipartFormData.Part<Source<ByteString, ?>>> multipartBody = new ArrayList<>();
        File originalFile = new File(ODFileUtils.getCurrentDir(PDF_FILE_2.path));
        Source<ByteString, ?> source = FileIO.fromPath(originalFile.toPath());

        multipartBody.add(new FilePart<>(DATAFILE, originalFile.getName(), "application/octet-stream", source));
        multipartBody.add(new DataPart(BODY, String.valueOf(generateBody(15, 0, 0))));

        request.bodyMultipart(multipartBody, mat);
        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");

        JsonNode expectedBody = generateBody(15, 0, 0);
        JsonNode dataJson = resultJson.get("data");

        assertEntryBody(expectedBody, dataJson);
        assertTrue("should have file_metadata field", dataJson.has("file_metadata"));
        assertFalse("should not file_metadata field null", dataJson.get("file_metadata").isNull());

        assertFileMetadata(dataJson, PDF);
    }

    @Test
    public void listWithDoubleDash() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(10, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=--");

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "Invalid query content: --");
    }

    @Test
    public void listWithPhrase() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        createEntry(10, 0, 1, PDF_FILE_1.path);
        createEntry(11, 1, 2, PDF_FILE_2.path);

        RequestBuilder request = new RequestBuilder().method(GET).uri("/data?content=like%20octothorpe");

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Listing entry ids");

        JsonNode data = resultJson.get("data");
        assertNotNull("should have count entry", data.get(SearchResult.COUNT));
        JsonNode entries = data.get(ENTRIES);
        assertTrue("should be an array of entries", entries.isArray());
        assertEquals("should exist only one entry created", 1, entries.size());
        assertEquals("should match entries size with count entry", data.get(SearchResult.COUNT).asLong(), entries.size());
    }

    private JsonNode getEntry(String id) throws IOException {
        RequestBuilder requestGet = new RequestBuilder().method(GET).uri("/data/" + id);
        Result resultGet = route(requestGet);
        assertResultIsOk(resultGet);
        return (new ObjectMapper()).readTree(contentAsString(resultGet));
    }

    private JsonNode createEntry(int id, int startOffset, int endOffset, String file) throws IOException {
        RequestBuilder request = new RequestBuilder().method(POST).path("/data");
        request.bodyMultipart(generateFormBody(id, startOffset, endOffset, file), mat);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode entry = (new ObjectMapper()).readTree(contentAsString(result));
        ObjectNode body = generateBody(id, startOffset, endOffset);
        assertEntryBody(body, entry.get("data"));

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");

        return resultJson.get("data");
    }
}
