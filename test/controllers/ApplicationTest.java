package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import controllers.Application;
import org.junit.*;

import play.api.mvc.*;
import play.mvc.*;
import play.test.*;
import play.data.DynamicForm;
import play.data.validation.ValidationError;
import play.data.validation.Constraints.RequiredValidator;
import play.i18n.Lang;
import play.libs.F;
import play.libs.F.*;
import play.twirl.api.Content;

import static play.test.Helpers.*;
import static org.junit.Assert.*;


/**
*
* Simple (JUnit) tests that can call all parts of a play app.
* If you are interested in mocking a whole application, see the wiki for more details.
*
*/
public class ApplicationTest {

    @Test
    /**
     * This should test if a catalog is correctly built and fetched.
     * The reference for the catalog can be found at the following url
     * http://docs.cloudfoundry.org/services/catalog-metadata.html
     */


    public void fetchCatalog() {
        Application app = new controllers.Application();
        //app.fetchCatalog();
        // #TODO: implement test of the catalog contents.

    }


}
