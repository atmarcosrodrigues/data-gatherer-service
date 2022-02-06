package com.silibrina.tecnova.opendata.cf;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * This class models the parameters used on the PUT method for provision.
 * The required parameters are:
 * organization_guid (string), plan_id (string), service_id (string), space_guid (string)
 * The optional parameters are:
 * parameters (JSON) and accepts_incomplete (boolean)
 */
public class ProvisionParameters {
    private Boolean accepts_incomplete;
    private JsonNode parameters;

    private String organization_guid;
    private String plan_id;
    private String space_guid;
    private String service_id;

    public ProvisionParameters(JsonNode json) throws IllegalArgumentException {
        try {
            this.organization_guid = json.get("organization_guid").textValue();
        } catch (Exception e) {
            try {
                this.organization_guid = json.get("organization_id").textValue();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Missing parameter [organization_guid]");
            }
        }

        try {
            this.plan_id = json.get("plan_id").textValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("Missing parameter [plan_id]");
        }

        try {
            this.space_guid = json.get("space_guid").textValue();
        } catch (Exception e) {
            try {
                this.space_guid = json.get("space_id").textValue();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Missing parameter [space_guid]");
            }
        }

        try {
            this.service_id = json.get("service_id").textValue();
        } catch (Exception e) {
            throw new IllegalArgumentException("Missing parameter [service_id]");
        }

        if(json.has("accepts_incomplete"))
            this.accepts_incomplete = json.get("accepts_incomplete").booleanValue();
        if(json.has("parameters"))
            this.parameters = json.findValue("parameters");

    }

    public Boolean getAccepts_incomplete() {
        return accepts_incomplete;
    }

    public void setAccepts_incomplete(Boolean accepts_incomplete) {
        this.accepts_incomplete = accepts_incomplete;
    }

    public JsonNode getParameters() {
        return parameters;
    }

    public void setParameters(JsonNode parameters) {
        this.parameters = parameters;
    }

    public String getOrganization_guid() {
        return organization_guid;
    }

    public void setOrganization_guid(String organization_guid) {
        this.organization_guid = organization_guid;
    }

    public String getPlan_id() {
        return plan_id;
    }

    public void setPlan_id(String plan_id) {
        this.plan_id = plan_id;
    }

    public String getService_id() {
        return service_id;
    }

    public void setService_id(String service_id) {
        this.service_id = service_id;
    }

    public String getSpace_guid() {
        return space_guid;
    }

    public void setSpace_guid(String space_guid) {
        this.space_guid = space_guid;
    }

    @Override
    public String toString() {
        return "ProvisionParameters{" +
                "accepts_incomplete=" + accepts_incomplete +
                ", parameters=" + parameters +
                ", organization_guid='" + organization_guid + '\'' +
                ", plan_id='" + plan_id + '\'' +
                ", service_id='" + service_id + '\'' +
                ", space_guid='" + space_guid + '\'' +
                '}';
    }


}
