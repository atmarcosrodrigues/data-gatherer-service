package com.silibrina.tecnova.opendata.cf;

import com.fasterxml.jackson.databind.JsonNode;

public class UpdateParameters {
    private Boolean accepts_incomplete;
    private JsonNode parameters;

    private String plan_id;
    private String service_id;


    /**
     * The previous values may be modeled as a ProvisionParameters object without accepts_incomplete and parameters attributes.
     */
    private ProvisionParameters previous_values;

    public UpdateParameters(JsonNode json) {
        this.previous_values = new ProvisionParameters(json.get("previous_values"));

        try {
            this.plan_id = json.get("plan_id").textValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("Missing parameter [plan_id]");

        }

        try {
            this.service_id = json.get("service_id").textValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("Missing parameter [service_id]");
        }

        if(json.has("accepts_incomplete"))
            this.accepts_incomplete = json.get("accepts_incomplete").booleanValue();
        if(json.has("parameters"))
            this.parameters = json.get("parameters");

    }

    @Override
    public String toString() {
        return "UpdateParameters{" +
                "accepts_incomplete=" + accepts_incomplete +
                ", parameters=" + parameters +
                ", plan_id='" + plan_id + '\'' +
                ", service_id='" + service_id + '\'' +
                ", previous_values=" + previous_values +
                '}';
    }
}
