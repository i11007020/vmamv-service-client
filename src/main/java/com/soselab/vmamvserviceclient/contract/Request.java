package com.soselab.vmamvserviceclient.contract;

import org.springframework.cloud.contract.spec.internal.QueryParameter;

import java.util.HashMap;
import java.util.List;

public class Request {

    private String method;
//    private List<QueryParameter> queryParameters;
    private HashMap<String,String> queryParameters;
    private String header;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public HashMap<String,String> getQps() {
        return queryParameters;
    }

    public void setQps(HashMap<String,String> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
