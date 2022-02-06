package controllers;

import com.silibrina.tecnova.commons.exceptions.InvalidConditionException;
import com.silibrina.tecnova.commons.exceptions.RouteNotFoundException;
import com.silibrina.tecnova.commons.exceptions.UnrecoverableErrorException;
import com.silibrina.tecnova.opendata.controller.OpenDataController;
import com.silibrina.tecnova.opendata.controller.SimpleOpenDataController;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

import javax.annotation.Nonnull;
import java.io.IOException;

import static com.silibrina.tecnova.opendata.utils.HeaderWrapper.resultSync;

/**
 * This class routes the calls to OpenData to predefined calls.
 * This is a necessary since developers creating their plugins must be able to create their own routes.
 * FIXME: make me async
 */
public class OpenData extends Controller {
    private static final Logger.ALogger logger = Logger.of(OpenData.class);
    private static final String PREFIX = "/opendata";

    private final OpenDataController controller;

    public OpenData() throws IOException {
        super();

        controller = new SimpleOpenDataController();
    }

    /**
     * FIXME: write javadoc
     * @param odRoute
     * @return
     */
    @SuppressWarnings("unused")
    @Nonnull
    public Result handle(@Nonnull final String odRoute) {
        try {
            return controller.handleContent(fixPrefix(odRoute));
        } catch (RouteNotFoundException e) {
            logger.info(e.getMessage());
            return resultSync(e);
        } catch (InvalidConditionException e) {
            logger.error(e.getMessage(), e);
            return resultSync(e);
        } catch (UnrecoverableErrorException e) {
            logger.error(e.getMessage(), e);
            System.exit(e.getExitStatus().exitStatus);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return internalServerError();
        }
        return internalServerError();
    }

    private String fixPrefix(String odRoute) {
        return odRoute.replaceFirst(PREFIX, "");
    }
}
