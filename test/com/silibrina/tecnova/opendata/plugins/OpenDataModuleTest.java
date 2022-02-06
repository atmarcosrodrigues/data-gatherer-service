package com.silibrina.tecnova.opendata.plugins;

import com.silibrina.tecnova.opendata.controller.SimpleOpenDataController;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;
import play.test.WithApplication;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;

public class OpenDataModuleTest extends WithApplication {
    private static final String OPENDATA_PREFIX = "/opendata";

    private SimpleOpenDataController pluginsController;

    @Before
    public void loadPluginsController() throws IOException {
        pluginsController = new SimpleOpenDataController();
    }

    @Test
    public void pluginRoutingGETToRootTest() {
        RequestBuilder request = new RequestBuilder();
        Result result = route(request);

        assertNotNull(result);
        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/json"), result.contentType());
        assertEquals("Ok. Service Broker seems to be up and running.", contentAsString(result));
    }

    @Test
    public void pluginRoutingGETTest() {
        RequestBuilder request = new RequestBuilder().path(OPENDATA_PREFIX + "/something/some/thing");
        Result result = route(request);

        assertNotNull(result);

        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/json"), result.contentType());
    }

    @Test
    public void loadingOpenDataPluginTest() {
        assertNotNull("OpenData Controller must no be null", pluginsController);
    }

    @Test
    public void pluginResponseTest() {
        final String content = "my-content";

        RequestBuilder request = new RequestBuilder().path(OPENDATA_PREFIX + "/something/some/thing");
        Result result = route(request);

        assertNotNull(result);

        assertEquals(OK, result.status());
        assertEquals(Optional.of("application/json"), result.contentType());
        assertEquals("Content should have been configured ", content, contentAsString(result));
    }


}
