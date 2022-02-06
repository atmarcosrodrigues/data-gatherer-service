package com.silibrina.tecnova.opendata.plugins;

import com.silibrina.tecnova.commons.annotations.Route;
import com.silibrina.tecnova.commons.modules.OpenDataModule;
import play.mvc.Result;

import static play.mvc.Results.ok;

/**
 * This class is used by the tests in service-broker.
 * E.g: {@link OpenDataModuleTest}
 */
@SuppressWarnings("unused")
public class PluginResponseTest extends OpenDataModule {

        @Route(path = "something/some/thing", method = "GET")
        public Result exec() {
            return ok("my-content").as("application/json");
        }


}