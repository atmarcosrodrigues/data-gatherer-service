package com.silibrina.tecnova.catalog;

import controllers.Application;
import org.junit.Test;
import play.mvc.Result;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static play.mvc.Http.Status.OK;

public class ProvisionInstanceTest {

    @Test
    public void provisionInstanceHeaderTest() {
        Result result = new Application().fetchCatalog();
        assertNotNull(result);
        assertEquals(result.status(), OK);
        assertEquals(Optional.of("application/json"), result.contentType());
    }

}
