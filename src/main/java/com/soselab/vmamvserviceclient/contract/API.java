package com.soselab.vmamvserviceclient.contract;

public class API {
    private ContractContent contractContent;
    private TestResult testResult;

    public ContractContent getContractContent() {
        return contractContent;
    }

    public void setContractContent(ContractContent contractContent) {
        this.contractContent = contractContent;
    }

    public TestResult getTestResult() {
        return testResult;
    }

    public void setTestResult(TestResult testResult) {
        this.testResult = testResult;
    }
}
