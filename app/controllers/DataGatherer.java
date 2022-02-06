package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.commons.conf.ConfigLoader;
import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.exceptions.UnrecoverableErrorException;
import com.silibrina.tecnova.commons.fs.FileSystem;
import com.silibrina.tecnova.commons.fs.FileSystemFactory;
import com.silibrina.tecnova.commons.model.EntryMetadata;
import com.silibrina.tecnova.opendata.gatherer.DataGatheringController;
import com.silibrina.tecnova.opendata.utils.Helper;
import play.Logger;
import play.api.Play;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static com.silibrina.tecnova.commons.utils.Preconditions.checkCondition;
import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;
import static com.silibrina.tecnova.opendata.utils.HeaderWrapper.result;

/**
 * Handles requests for CRUD of an entry (except for content download), which is
 * basically data that is made available.
 *
 * Note that the normal behaviour for requests received here when an error
 * occurs is to respond with:
 *  406 (NOT_ACCEPTABLE): an error occurred and it is probably a malformed request.
 *  500 (INTERNAL_SERVER_ERROR): an internal error happened and we should be ashamed
 * and pray for the gods to forgive us (also fix the bug).
 */
public class DataGatherer extends Controller {
    private static final Logger.ALogger logger = Logger.of(DataGatherer.class);
    private static final String MULTIPART_FORM_DATA = "multipart/form-data";
    private final DataGatheringController dataGatherer;

    public static final String DATAFILE = "datafile";
    public static final String BODY = "body";

    public DataGatherer() {
        super();

        ConfigLoader.setUp();
        dataGatherer = Play.current().injector().instanceOf(DataGatheringController.class);
        createBaseDir();
    }

    private void createBaseDir() {
        try {
            FileSystem fileSystem = FileSystemFactory.getFileSystem();
            fileSystem.createBasedir();
        } catch (IOException e) {
            logger.error("An error happened while trying to create basedir.", e);
        }
    }

    /**
     * POST    /data
     *
     * Creates an entry with metadata information about it.
     * It expects to receive the information about
     * the entry as a json body or multipart/form-data.
     *
     * The information in a multipart/form-data can be as in a regular form,
     * where each field is a param in the form plus an optional file
     * or a param (body) with the json body and a file param (datafile).
     *
     * To know more about the parameters, please see constants at {@link EntryMetadata}.
     *
     * @return Updated json representation of the {@link EntryMetadata}.
     */
    @SuppressWarnings("unused")
    public CompletionStage<Result> create() {
        logger.debug("-> create");
        try {
            FilePart file = getFile();

            JsonNode body = getBody();

            checkNotNullCondition("Invalid entry body", body);
            return result("Entry created", dataGatherer.create(processBody(body), file));
        } catch (InvalidConditionException | ParseException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("<- create");
        }

        return result(internalServerError());
    }

    /**
     * POST    /data/uploadFile/:id
     * PUT     /data/uploadFile/:id
     *
     * Uploads a file to an exiting entry. This file will be indexed and
     * based on its original format, will be converted to many formats.
     *
     * File must be passed in multipart/form-data in a datafile parameter.
     * File as raw body is deprecated.
     *
     * @param id the id of the entry to have the file uploaded.
     *
     * @return Updated json representation of the {@link EntryMetadata}.
     */
    @SuppressWarnings("unused")
    public CompletionStage<Result> upload(String id) {
        logger.debug("-> upload");
        try {
            FilePart file = getFile();

            checkNotNullCondition("Missing file", file);
            checkNotNullCondition("Invalid id", id);

            return result("File uploaded", dataGatherer.upload(id, file));
        } catch (InvalidConditionException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("<- upload");
        }
        return result(internalServerError());
    }

    /**
     * PUT     /data/:id
     *
     * Updates information of an existing entry. It expects to receive the information
     * about the entry as a json body or multipart/form-data.
     *
     * The information in a multipart/form-data can be as a regular form,
     * where each field is a param in the form plus an optional file
     * or a param (body) with the json body and a file param (datafile).
     *
     * To know more about the parameters, please see constants at {@link EntryMetadata}.
     *
     * @param id the id of the entry to have the file and/or body updated.
     *
     * @return Updated json representation of the {@link EntryMetadata}.
     */
    @SuppressWarnings("unused")
    public CompletionStage<Result> update(String id) {
        logger.debug("-> update");
        try {
            FilePart file = getFile();
            JsonNode body = getBody();

            return result("Entry updated", dataGatherer.update(id, processBody(body), file));
        } catch (InvalidConditionException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("<- update");
        }
        return result(internalServerError());
    }

    /**
     * DELETE  /data/:id
     *
     * Deletes an existing entry with its existing dependencies
     * (files, indexes etc).
     *
     * @param id the id of the entry.
     *
     * @return json representation of the deleted {@link EntryMetadata}.
     */
    @SuppressWarnings("unused")
    public CompletionStage<Result> delete(String id) {
        logger.debug("-> delete");
        try {
            checkNotNullCondition("Invalid id", id);
            return result("Entry deleted", dataGatherer.delete(id));
        } catch (InvalidConditionException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("<- delete");
        }
        return result(internalServerError());
    }

    /**
     * GET     /data
     *
     * Lists (search) stored entries, with a maximum of 500 entries per request (page).
     *
     * Default values for page is 0 and number of entries is 50 and all values for the
     * query must be given as query param.
     *
     * If an empty request is made (or including only 'limit' and 'page'), entries will be
     * retrieved without filters.
     *
     * It is also possible to include parameters for the query with the same fields used
     * to create an entry with an addition of 'content'. The content field is a general
     * query parameters to find files by the content, but it is also applied to the metadata
     * entry, meaning that a given content will be found in entry metadata even if it is
     * not part of the file content. The result of this query can be filtered by the other
     * fields of an entry.
     *
     * E.g.
     * The following query would retrieve 50 entries of the first page containing octothorpe
     * in its file of metadata content. This entries would be filtered by those whose org is
     * MyOrg.
     *
     * curl -X GET http://xxx/data?org=MyOrg&content=octothorpe
     *
     * To know more about the parameters, please see constants at {@link EntryMetadata}.
     *
     * @return json array representation of the {@link EntryMetadata} found matching the
     * given query.
     */
    @SuppressWarnings("unused")
    public CompletionStage<Result> list() {
        logger.debug("-> list");
        try {
            return result("Listing entry ids", dataGatherer.list(processBody(queryToJson())));
        } catch (InvalidConditionException | ParseException
                | org.apache.lucene.queryparser.classic.ParseException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("<- list");
        }
        return result(internalServerError());
    }

    /**
     * GET     /data/:id
     *
     * Get full metadata information for an entry.
     *
     * @param id the id of the entry.
     *
     * @return json representation of the deleted {@link EntryMetadata}.
     */
    @SuppressWarnings("unused")
    public CompletionStage<Result> show(String id) {
        logger.debug("-> show");
        try {
            checkNotNullCondition("Invalid id", id);

            return result("Show entry", dataGatherer.show(id));
        } catch (InvalidConditionException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        } finally {
            logger.debug("<- show");
        }
        return result(internalServerError());
    }

    private FilePart getFile() {
        MultipartFormData body = request().body().asMultipartFormData();
        return body == null || body.getFiles().isEmpty() ? null : getFileFromMultipart(body);
    }

    private FilePart getFileFromMultipart(MultipartFormData body) {
        FilePart dataFile = body.getFile(DATAFILE);
        if (dataFile != null) {
            return dataFile;
        }
        return (FilePart) body.getFiles().get(0);
    }

    private JsonNode getBody() throws IOException {
        Http.Request request = request();
        Optional<String> optionalContentType = request.contentType();

        checkCondition("Content type not given", optionalContentType.isPresent());

        String contentType = optionalContentType.isPresent() ? optionalContentType.get() : Http.MimeTypes.TEXT;

        switch (contentType) {
            case Http.MimeTypes.JSON:
                return request.body().asJson();
            case Http.MimeTypes.FORM:
                return mapToJson(request.body().asFormUrlEncoded());
            case MULTIPART_FORM_DATA:
                return multipartToJson(request.body().asMultipartFormData().asFormUrlEncoded());
            default:
                return queryToJson();
        }
    }

    private JsonNode processBody(JsonNode body) {
        ObjectNode result = Json.newObject();
        for(String param: Helper.fields) {
            if(body.has(param))
                if (body.get(param).size() >= 1)
                    result.set(param, body.get(param).get(0));
                else
                    result.set(param, body.get(param));
        }

        return result;
    }

    private JsonNode queryToJson() {
        Map<String, String[]> data = request().queryString();

        return mapToJson(data);
    }

    private JsonNode multipartToJson(Map<String, String[]> data) throws IOException {
        ObjectNode body = Json.newObject();
        if (data.containsKey(BODY)) {
            body = (ObjectNode) (new ObjectMapper()).readTree(data.get(BODY)[0]);
        }
        return mapToJson(data, body);
    }

    private JsonNode mapToJson(Map<String, String[]> data) {
        return mapToJson(data, Json.newObject());
    }

    private JsonNode mapToJson(Map<String, String[]> data, ObjectNode body) {
        for (String key : data.keySet()) {
            String[] values = data.get(key);
            if (values != null) {
                if (values.length >= 1) {
                    body.put(key, values[0]);
                }
            }

        }
        return body;
    }
}
