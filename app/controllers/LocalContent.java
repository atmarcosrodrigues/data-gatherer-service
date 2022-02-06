package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.exceptions.UnrecoverableErrorException;
import com.silibrina.tecnova.opendata.gatherer.storage.fs.local.FileSystemController;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static com.silibrina.tecnova.opendata.utils.HeaderWrapper.result;
import static com.silibrina.tecnova.opendata.utils.JsonHelper.mapToJson;

/**
 * Handles requests to download file locally stored.
 *
 * Note that the normal behaviour for requests received here when an error
 * occurs is to respond with:
 *  406 (NOT_ACCEPTABLE): an error occurred and it is probably a malformed request.
 *  500 (INTERNAL_SERVER_ERROR): an internal error happened and we should be ashamed
 * and pray for the gods to forgive us (also fix the bug).
 */
public class LocalContent extends Controller {
    private static final Logger.ALogger logger = Logger.of(DataGatherer.class);

    private final FileSystemController fs;

    public LocalContent() throws IOException {
        super();

        fs = new FileSystemController();
    }

    /**
     * GET     /content/:id
     *
     * Provides a file for downloading in its original or parsed format, if
     * this file is locally stored and the format is available for the given
     * file.
     *
     * To consult the list of available formats, please see the json representation
     * of the entry using: GET     /data/:id
     *
     * @param type the type (format, version) of the file to retrieve.
     * @param filename the id of the entry.
     *
     * @return the file.
     */
    public CompletionStage<Result> get(String type, String filename) {
        try {
            return result(fs.get(type, filename, queryToJson()));
        } catch (InvalidConditionException e) {
            logger.info(e.getMessage());
            return result(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
        return result(internalServerError());
    }

    private JsonNode queryToJson() {
        Map<String, String[]> data = request().queryString();

        return mapToJson(data);
    }
}
