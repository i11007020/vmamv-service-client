package com.soselab.vmamvserviceclient.contract;

import java.util.ArrayList;

public class QueryParameters {
    private ArrayList<?> parameters = new ArrayList<>();

    public ArrayList<?> getParameters() {
        return parameters;
    }

    public void setParameters(ArrayList<?> parameters) {
        this.parameters = parameters;
    }
}
