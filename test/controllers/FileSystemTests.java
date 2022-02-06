package controllers;

import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.silibrina.tecnova.coherence.CoherenceMain;
import com.silibrina.tecnova.commons.fs.FileHandle;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.fs.local.LocalFileSystem;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.commons.model.file.FileMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils;
import com.silibrina.tecnova.conversion.ConversionMain;
import com.silibrina.tecnova.conversion.fs.parser.JsonParser;
import com.silibrina.tecnova.conversion.fs.parser.OriginalParser;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import play.Logger;
import play.core.j.JavaResultExtractor;
import play.mvc.Http;
import play.mvc.Result;
import play.test.WithApplication;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static com.silibrina.tecnova.commons.conf.ConfigConstants.Strings.GATHERER_FS;
import static com.silibrina.tecnova.commons.fs.InputStreamAttrExtractor.extractAttr;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getCurrentDir;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.*;
import static com.silibrina.tecnova.commons.utils.TestsUtils.cleanUpEntries;
import static com.silibrina.tecnova.commons.utils.TestsUtils.generateFileName;
import static com.silibrina.tecnova.opendata.utils.TestsUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static play.test.Helpers.*;

public class FileSystemTests extends WithApplication {
    private static final Logger.ALogger logger = Logger.of(FileSystemTests.class);

    private CoherenceMain coherenceMain;
    private ConversionMain conversionMain;
    private FileSystem fileSystem;

    @Before
    public void setUp() throws IOException, ConfigurationException, TimeoutException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        System.setProperty(GATHERER_FS.field, "local");
        ConfigFactory.invalidateCaches();

        cleanUpEntries();

        fileSystem = new LocalFileSystem();

        coherenceMain = new CoherenceMain(false);
        coherenceMain.start();

        conversionMain = new ConversionMain(false, fileSystem);
        conversionMain.start();

        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @After
    public void cleanUp() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());
        coherenceMain.stopCoherence();
        conversionMain.stopConverter();

        cleanUpEntries();
        logger.debug("<== {} finished", new Object(){}.getClass().getEnclosingMethod().getName());
    }

    @Test
    public void contentOfXlsTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(1, 0, 0, XLS_FILE_1, mat);

        JsonNode version = getVersion(entry1, OriginalParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = URI.create(version.get(FileVersion.URL).asText()).getPath();
        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);

        Result result = route(request);
        assertNotNull(result);
        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/excel"), result.contentType());

        ByteString body = JavaResultExtractor.getBody(result, 100000L, mat);

        FileHandle tmp = fileSystem.open(OriginalParser.TYPE, generateFileName());
        FileUtils.writeByteArrayToFile(new File(tmp.toURI()), body.toArray());

        assertEquals("should have the same content (hash)",
                extractAttr(new File(getCurrentDir(XLS_FILE_1.path))), extractAttr(new File(tmp.toURI())));
    }

    @Test
    public void contentOfXlsToJsonTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(2, 0, 0, XLS_FILE_1, mat);

        JsonNode version = getVersion(entry1, JsonParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = URI.create(version.get(FileVersion.URL).asText()).getPath();
        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result, mat));
        assertTrue("should have some content", resultJson.toString().length() > 4000);
    }

    @Test
    public void contentOfXlsxTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(1, 0, 0, XLSX_FILE_1, mat);
        JsonNode version = getVersion(entry1, OriginalParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = URI.create(version.get(FileVersion.URL).asText()).getPath();
        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);

        Result result = route(request);
        assertNotNull(result);
        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), result.contentType());

        ByteString body = JavaResultExtractor.getBody(result, 100000L, mat);

        FileHandle tmp = fileSystem.open(OriginalParser.TYPE, generateFileName());
        FileUtils.writeByteArrayToFile(new File(tmp.toURI()), body.toArray());

        assertEquals("should have the same content (hash)",
                extractAttr(new File(getCurrentDir(XLSX_FILE_1.path))), extractAttr(new File(tmp.toURI())));
    }

    @Test
    public void contentOfXlsxToJsonTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(2, 0, 0, XLSX_FILE_1, mat);
        JsonNode version = getVersion(entry1, JsonParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = URI.create(version.get(FileVersion.URL).asText()).getPath();
        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result, mat));
        assertTrue("should have some content", resultJson.toString().length() > 4000);
    }

    @Test
    public void contentOfPngTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());


        JsonNode entry1 = createEntryByRequest(3, 0, 0, PNG_FILE_1, mat);
        JsonNode version = getVersion(entry1, OriginalParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = URI.create(version.get(FileVersion.URL).asText()).getPath();
        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);

        Result result = route(request);
        assertNotNull(result);
        assertEquals(OK, result.status());
        assertEquals(Optional.of("image/png"), result.contentType());
        ByteString body = JavaResultExtractor.getBody(result, 100000L, mat);

        FileHandle tmp = fileSystem.open(OriginalParser.TYPE, generateFileName());
        FileUtils.writeByteArrayToFile(new File(tmp.toURI()), body.toArray());

        assertEquals("should have the same content (hash)",
                extractAttr(new File(getCurrentDir(PNG_FILE_1.path))), extractAttr(new File(tmp.toURI())));
    }

    @Test
    public void contentOfPngToJsonTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(3, 0, 0, PNG_FILE_1, mat);
        String id = entry1.get("_id").asText();

        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri("/storage/json/" + id);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderError(resultJson, "This file does not support this format.");
    }

    @Test
    public void contentOfPDFTest() throws Exception {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(3, 0, 0, PDF_FILE_1, mat);
        JsonNode version = getVersion(entry1, OriginalParser.TYPE);
        assertNotNull("version should not be null", version);

        String url = URI.create(version.get(FileVersion.URL).asText()).getPath();
        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);

        Result result = route(request);
        assertNotNull(result);
        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/pdf"), result.contentType());
        ByteString body = JavaResultExtractor.getBody(result, 100000L, mat);

        FileHandle tmp = fileSystem.open(OriginalParser.TYPE, generateFileName());
        FileUtils.writeByteArrayToFile(new File(tmp.toURI()), body.toArray());

        assertEquals("should have the same content (hash)",
                extractAttr(new File(getCurrentDir(PDF_FILE_1.path))), extractAttr(new File(tmp.toURI())));
    }

    @Test
    public void contentOFilesTest() throws IOException, URISyntaxException {
        FilesPathUtils[] files = FilesPathUtils.values();
        for (FilesPathUtils file : files) {
            contentOfTypeTest(file);
        }
    }

    private void contentOfTypeTest(FilesPathUtils file) throws IOException, URISyntaxException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(3, 0, 0, file, mat);
        JsonNode fileMetadata = entry1.get(EntryMetadata.FILE_METADATA);
        ArrayNode versions = (ArrayNode) fileMetadata.get(FileMetadata.VERSIONS);

        for (JsonNode node : versions) {
            String url = URI.create(node.get(FileVersion.URL).asText()).getPath();

            Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(url);
            Result result = route(request);

            assertNotNull(result);
            assertEquals(OK, result.status());

            ByteString body = JavaResultExtractor.getBody(result, 100000L, mat);

            byte[] data = body.toArray();
            assertNotNull("body should not be null", data);
            assertTrue("should have more than 100 bytes", data.length > 10);
        }
    }

    @Test
    public void contentOfPDFToJsonTest() throws IOException {
        logger.debug("==> {}", new Object(){}.getClass().getEnclosingMethod().getName());

        JsonNode entry1 = createEntryByRequest(3, 0, 0, PDF_FILE_1, mat);
        String id = entry1.get("_id").asText();

        Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri("/storage/json/" + id);

        Result result = route(request);
        assertResultIsError(result);

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        logger.debug("resultJson: {}", resultJson);
        checkHeaderError(resultJson, "This file does not support this format.");
    }
}
