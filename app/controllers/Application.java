package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.silibrina.tecnova.opendata.cf.ProvisionParameters;
import com.silibrina.tecnova.opendata.cf.UpdateParameters;
import com.silibrina.tecnova.opendata.utils.ODFileUtils;
import play.libs.Json;
import play.mvc.*;

import play.Logger;

import java.io.File;

@SuppressWarnings("unused")
public class Application extends Controller {
    private final static String CATALOG_FILE = ODFileUtils.getCurrentDir("data/catalog.json");

    private final Logger.ALogger logger = Logger.of(Application.class);

    /**
     * 202 ACCEPTED, 410 GONE
     */
    public Result getLastOperation(long instance_id) {
        return Results.TODO;
    }

    /**
     * 200 OK, 202 Accepted, 422 Unprocessable entity
     */
    public Result updateInstance(long instance_id) {
        JsonNode json = request().body().asJson();
        ObjectNode result = Json.newObject();

        try {
            UpdateParameters up = new UpdateParameters(json);
        } catch (Exception ex) {
            result.put("description", ex.getMessage());
            return badRequest(result);
        }
        return Results.TODO;
    }

    /**
     * 201 Created, 200 OK, 409 Conflict, 422 Unprocessable Entity
     */
    public Result createBind(long instance_id, long binding_id) {
        return Results.TODO;
    }

    /**
     * 200 OK, 410 Gone
     */
    public Result deleteBind(long instance_id, long binding_id) {
        return Results.TODO;
    }

    /**
     * 200 OK, 202 Accepted, 410 Gone, 422 Unprocessable Entity
     */
    public Result deprovision(long instance_id, String service_id, String plan_id, Boolean accepts_incomplete) {
        logger.debug("instance_id: "+ instance_id + "\nplan_id: " + plan_id + "\nservice_id: " + service_id + "\n accepts_incomplete: " + accepts_incomplete);
        return Results.TODO;
    }

    public Result index() {
        return ok("Ok. Service Broker seems to be up and running.").as("application/json");
    }

    /**
     * /v2/catalog
     */
    public Result fetchCatalog() {
        return ok(new File(CATALOG_FILE));
    }

    /**
     * /v2/service_instance/:id
     * 201 CREATED, 202 ACCEPTED, 200 OK, 409 Conflict
     * id?accepts_incomplete=true Check if id contains this parameter
     * if it fails to deliver the response within 60 seconds the code 422 UNPROCESSABLE ENTITY should be served.
     */
     public Result provisionInstance(Long id) {
         JsonNode json = request().body().asJson();
         ObjectNode result = Json.newObject();

         ProvisionParameters p = null;
         try {
             p = new ProvisionParameters(json);
         } catch (Exception ex) {
             result.put("description", ex.getMessage());
             return badRequest(result);
         }

         return ok("Just some test\n");
    }
}
