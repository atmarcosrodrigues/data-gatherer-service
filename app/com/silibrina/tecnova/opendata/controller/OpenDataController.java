package com.silibrina.tecnova.opendata.controller;

import play.mvc.Result;

/**
 * Handles an opendata request to a proper registered plugin.
 */
public interface OpenDataController {

    /**
     * Handles the request, passing it to the appropriated registered method (module).
     * It throws an exception when a route does not match any given method.
     *
     * @param odRoute the path of this request.
     *
     * @return the play result for this request.
     */
    Result handleContent(String odRoute);

}
