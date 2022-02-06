package com.silibrina.tecnova.opendata.controller;

import com.google.inject.Singleton;
import com.silibrina.tecnova.commons.modules.loader.RouteTable;
import com.silibrina.tecnova.commons.modules.loader.SimpleRouteTable;
import com.silibrina.tecnova.commons.modules.route.MethodRoute;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.annotation.Nonnull;
import java.io.IOException;

import static com.silibrina.tecnova.commons.utils.Preconditions.checkNotNullCondition;

/**
 * Handles the request by routing it to the appropriated route method.
 */
@Singleton
public class SimpleOpenDataController implements OpenDataController {
    private final RouteTable routeTable;

    public SimpleOpenDataController() throws IOException {
        routeTable = new SimpleRouteTable();
    }

    @Override
    public Result handleContent(@Nonnull String odRoute) {
        Http.Request httpMethod = Controller.request();

        MethodRoute route = routeTable.getRoute(httpMethod.method(), odRoute);

        checkNotNullCondition("Route not found", route);
        return execRoutes(route, odRoute, httpMethod);
    }

    private Result execRoutes(MethodRoute route, String odRoute, Http.Request httpMethod) {
        route.getPathPattern().matches(odRoute);
        return route.execute(odRoute, httpMethod);
    }
}
