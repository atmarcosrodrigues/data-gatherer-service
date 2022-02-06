package com.silibrina.tecnova.catalog;

import controllers.Application;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import play.mvc.Result;
import play.test.WithApplication;

import java.util.Optional;

import static org.junit.Assert.*;
import static play.test.Helpers.OK;
import static play.test.Helpers.contentAsString;

/**
 * Test for fetching catalog following the guide bellow.
 * http://docs.cloudfoundry.org/services/api.html
 */
public class FetchCatalogTest extends WithApplication {

    @Test
    public void fetchCatalogHeaderTest() {
        Result result = new Application().fetchCatalog();
        assertNotNull(result);
        assertEquals(result.status(), OK);
        assertEquals(Optional.of("application/json"), result.contentType());
    }

    @Test
    public void fetchCatalogMandatoryFieldTest() throws JSONException {
        Result result = new Application().fetchCatalog();
        assertNotNull(result);

        JSONObject jsonObject = new JSONObject(contentAsString(result, mat));
        assertTrue(jsonObject.has("services"));

        JSONArray servicesArray = jsonObject.getJSONArray("services");
        for (int i = 0; i < servicesArray.length(); i++) {
            JSONObject servicesJson = servicesArray.getJSONObject(i);
            assertTrue(servicesJson.has("id"));
            assertTrue(servicesJson.has("name"));
            assertTrue(servicesJson.has("description"));
            assertTrue(servicesJson.has("bindable"));
            assertTrue(servicesJson.has("plans"));

            JSONArray plansArray = servicesJson.getJSONArray("plans");
            for (int j = 0; j < plansArray.length(); j++) {
                JSONObject planJson = plansArray.getJSONObject(j);
                assertTrue(planJson.has("id"));
                assertTrue(planJson.has("name"));
                assertTrue(planJson.has("description"));
            }
        }
    }
}
