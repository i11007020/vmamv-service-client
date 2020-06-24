package com.soselab.vmamvserviceclient.contract;

import org.springframework.cloud.contract.spec.internal.QueryParameter;

import java.util.List;

public class Request {

    private String method;
    private List<QueryParameter> qps;
    private String header;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<QueryParameter> getQps() {
        return qps;
    }

    public void setQps(List<QueryParameter> qps) {
        this.qps = qps;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }
}
