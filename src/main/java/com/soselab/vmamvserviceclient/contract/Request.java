package com.soselab.vmamvserviceclient.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashMap;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Request {

    private String method;
    private HashMap<String,String> queryParameters;
    private String header;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public HashMap<String, String> getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(HashMap<String, String> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
