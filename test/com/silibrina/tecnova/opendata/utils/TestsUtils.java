package com.silibrina.tecnova.opendata.utils;

import akka.stream.Materializer;
import akka.stream.javadsl.FileIO;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.coherence.indexer.LuceneIndexer;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.model.file.FileMetadata;
import com.silibrina.tecnova.commons.model.file.FileVersion;
import com.silibrina.tecnova.commons.utils.ConstantUtils;
import com.silibrina.tecnova.conversion.fs.FormatFactory;
import com.silibrina.tecnova.opendata.gatherer.searcher.tasks.SearchEntryTask;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import org.apache.tika.io.TikaInputStream;
import play.libs.Json;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData.DataPart;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.silibrina.tecnova.commons.model.EntryMetadata.*;
import static com.silibrina.tecnova.commons.model.file.FileMetadata.ORIGINAL_NAME;
import static com.silibrina.tecnova.commons.utils.CommonsFileUtils.getInputStream;
import static com.silibrina.tecnova.commons.utils.ConstantUtils.FilesPathUtils.PDF_FILE_2;
import static com.silibrina.tecnova.commons.utils.TestsUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.POST;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.route;

public class TestsUtils {

    public static List<Http.MultipartFormData.Part<Source<ByteString, ?>>> generateFormBody(int id, int startOffset, int endOffset, String file) {
        List<Http.MultipartFormData.Part<Source<ByteString, ?>>> multipartBody = new ArrayList<>();
        File originalFile = new File(ODFileUtils.getCurrentDir(file));
        Source<ByteString, ?> source = FileIO.fromPath(originalFile.toPath());
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

        multipartBody.add(new FilePart<>("datafile", originalFile.getName(), "application/octet-stream", source));
        multipartBody.add(new DataPart(TITLE, "some_title_" + id));
        multipartBody.add(new DataPart(AUTHOR, "some_author_" + id));
        multipartBody.add(new DataPart(DESCRIPTION, "some_description_" + id));
        multipartBody.add(new DataPart(ORG, "some_org_" + id));
        multipartBody.add(new DataPart(UPLOADER, "some_uploader_" + id));
        multipartBody.add(new DataPart(PERIOD_START, formatter.format(generateDate(startOffset))));
        multipartBody.add(new DataPart(PERIOD_END, formatter.format(generateDate(endOffset))));
        return multipartBody;
    }

    public static FilePart getFileAsFilePart(String file) {
        File originalFile = new File(ODFileUtils.getCurrentDir(file));
        return new FilePart<>("datafile", originalFile.getName(), "application/octet-stream", originalFile);
    }

    public static List<Http.MultipartFormData.Part<Source<ByteString, ?>>> getFileAsMultipartFormData(String file) {
        File originalFile = new File(ODFileUtils.getCurrentDir(file));
        List<Http.MultipartFormData.Part<Source<ByteString, ?>>> multipartBody = new ArrayList<>();
        Source<ByteString, ?> source = FileIO.fromPath(originalFile.toPath());
        multipartBody.add(new FilePart<>("datafile", originalFile.getName(), "application/octet-stream", source));
        return multipartBody;
    }

    public static String generateBodyUriString(int id, int startOffset, int endOffset) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
        String start = formatter.format(generateDate(startOffset));
        String end = formatter.format(generateDate(endOffset));
        return String.format(Locale.getDefault(),
                        "?" + TITLE + "=some_title_%d"
                        + "&" + AUTHOR + "=some_author_%d"
                        + "&" + DESCRIPTION + "=some_description_%d"
                        + "&" + ORG + "=some_org_%d"
                        + "&" + PERIOD_START + "=%s"
                        + "&" + PERIOD_END +"=%s"
                        + "&" + UPLOADER + "=some_uploader_%d",
                id, id, id, id, start, end, id);
    }

    public static void assertResultIsOk(Result result) {
        assertNotNull(result);
        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/json"), result.contentType());
    }

    public static void assertResultIsError(Result result) {
        assertNotNull(result);
        assertEquals(BAD_REQUEST, result.status());
        assertEquals(Optional.of("application/json"), result.contentType());
    }

    public static void checkHeaderOk(JsonNode node, String message) {
        assertTrue("Should have a status field", node.has("status"));
        assertEquals("Status should be ok", "Ok", node.get("status").asText());
        assertTrue("Should have a message field", node.has("message"));
        assertEquals("Status should be " + message, message, node.get("message").asText());
        assertTrue("Should have a message field", node.has("data"));
    }

    public static void checkHeaderError(JsonNode node, String message) {
        assertTrue("Should have a status field", node.has("status"));
        assertEquals("Status should be error", "Error", node.get("status").asText());
        assertTrue("Should have a message field", node.has("message"));
        assertEquals("Status should be " + message, message, node.get("message").asText());
        assertTrue("Should have a data field", node.has("data"));
    }

    public static void assertEntryBody(JsonNode expected, JsonNode actual) {
        assertEquals("should be the same start period", expected.get(PERIOD_START).asText(), actual.get(PERIOD_START).asText());
        assertEquals("should be the same end period", expected.get(PERIOD_END).asText(), actual.get(PERIOD_END).asText());
        assertNotNull("should have gotten some createdAt date", actual.get(CREATED_AT));
        assertNotNull("should have gotten some updatedAt date", actual.get(UPDATED_AT));
        assertNotNull("should have gotten some status", actual.get(STATUS));
        assertEquals("should be the same author", expected.get(AUTHOR).asText(), actual.get(AUTHOR).asText());
        assertEquals("should be the same org", expected.get(ORG).asText(), actual.get(ORG).asText());
        assertEquals("should be the same uploader", expected.get(UPLOADER).asText(), actual.get(UPLOADER).asText());
        assertEquals("should be the same title", expected.get(TITLE).asText(), actual.get(TITLE).asText());
        assertEquals("should be the same description", expected.get(DESCRIPTION).asText(), actual.get(DESCRIPTION).asText());
    }


    public static JsonNode createEntryByRequest(int id, int startOffset, int endOffset, ConstantUtils.FilesPathUtils file, Materializer mat) throws IOException {
        Http.RequestBuilder request = new Http.RequestBuilder().method(POST).path("/data");
        request.bodyMultipart(generateFormBody(id, startOffset, endOffset, file.path), mat);

        Result result = route(request);
        assertResultIsOk(result);

        JsonNode entry = (new ObjectMapper()).readTree(contentAsString(result));
        ObjectNode body = generateBody(id, startOffset, endOffset);
        assertEntryBody(body, entry.get("data"));

        JsonNode resultJson = (new ObjectMapper()).readTree(contentAsString(result));
        checkHeaderOk(resultJson, "Entry created");

        return resultJson.get("data");
    }

    public static EntryMetadata createEntry(JsonNode body, ConstantUtils.FilesPathUtils file,
                                            FileSystem fileSystem) throws Exception {
        EntryMetadata entry = EntryMetadata.buildEntryMetaData(body);
        entry.save();

        if (file != null) {
            FileMetadata fileMetadata = generateFileMetadata(file, fileSystem);
            entry.setFileMetadata(fileMetadata);
            entry.save();
        }

        return entry;
    }

    public static EntryMetadata updateEntry(EntryMetadata entry, JsonNode body, ConstantUtils.FilesPathUtils file,
                                            FileSystem fileSystem) throws Exception {
        entry.updateEntry(body);

        if (file != null) {
            FileMetadata fileMetadata = generateFileMetadata(file, fileSystem);
            entry.setFileMetadata(fileMetadata);
        }

        entry.save();
        return entry;
    }

    public static ObjectNode listAllQuery() {
        ObjectNode query = Json.newObject();
        query.put(SearchEntryTask.PAGE, 0);
        query.put(SearchEntryTask.LIMIT, 500);
        return query;
    }

    public static String listAllQueryParams() {
        return SearchEntryTask.PAGE + "=0&" + SearchEntryTask.LIMIT + "=500";
    }

    public static void createIndex(LuceneIndexer indexer, EntryMetadata entry, FileSystem fileSystem) throws Exception {
        generateFormats(entry, fileSystem);

        URI filToIndex = getFormatToIndex(entry);

        try (InputStream stream = getInputStream(filToIndex)) {
            stream.mark(0);
            indexer.createIndex(entry, stream);
        }
    }

    private static URI getFormatToIndex(EntryMetadata entry) {
        return entry.getFileMetadata().getVersion("txt").getPath();
    }

    private static void generateFormats(EntryMetadata entry, FileSystem fileSystem) throws Exception {
        FileVersion originalVersion = entry.getFileMetadata().getVersion("original");

        ObjectNode payload = Json.newObject();
        payload.put(FileVersion.PATH, String.valueOf(originalVersion.getPath()));
        payload.put(ORIGINAL_NAME, new File(PDF_FILE_2.path).getName());

        new FormatFactory(fileSystem).create(entry, payload);
    }

    public static void updateIndex(LuceneIndexer indexer, EntryMetadata entry, FileSystem fileSystem) throws Exception {
        generateFormats(entry, fileSystem);
        URI filToIndex = getFormatToIndex(entry);
        try (TikaInputStream stream = TikaInputStream.get(filToIndex)) {
            stream.mark(0);
            indexer.updateIndex(entry, stream);
        }
    }

    public static JsonNode getVersion(JsonNode entry, String type) {
        JsonNode fileMetadata = entry.get(EntryMetadata.FILE_METADATA);
        ArrayNode versions = (ArrayNode) fileMetadata.get(FileMetadata.VERSIONS);
        for (JsonNode version : versions) {
            String format = version.get(FileVersion.FORMAT).asText();
            if (format.equals(type)) {
                return version;
            }
        }

        return null;
    }
}
