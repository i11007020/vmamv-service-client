package com.soselab.vmamvserviceclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import springfox.documentation.service.ObjectVendorExtension;
import springfox.documentation.service.StringVendorExtension;
import springfox.documentation.service.VendorExtension;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.io.File;

public class ContractAnalyzer2 {

    private static final Logger logger = LoggerFactory.getLogger(ContractAnalyzer2.class);
    private static final String GET = "get", POST = "post", PUT = "put", PATCH = "patch", DELETE = "delete";

    public List<VendorExtension> swaggerExtension(String filepath_groovy, String filepath_mappings, String filepath_testXml, String appName) throws Exception {
        ArrayList<String> contractSource = new ArrayList<>();
        ArrayList<String> mappingSource = new ArrayList<>();

        System.out.println("filepath_groovy: ");
        readFile(filepath_groovy);
        System.out.println("filepath_mappings: ");
        //readFile(filepath_mappings);


/*        String contractSource = readfile_groovy(filepath_groovy);


        ObjectVendorExtension extension;

        if(contractSource == null || contractSource.equals("")) {
            extension = new ObjectVendorExtension("x-contract");
        } else {
                extension = getContractProperty(contractSource, filepath_testXml, appName);
        }
        return Collections.singletonList(extension);*/
        return Collections.singletonList(new ObjectVendorExtension("x-contract"));
    }




    public void readFile(String fileDir){


        List<File> fileList = new ArrayList<>();
        File file = new File(this.getClass().getResource(fileDir).getPath());
        File[] files = file.listFiles();// 獲取目錄下的所有檔案或資料夾
        if (files == null) {// 如果目錄為空，直接退出
            return;
        }
        // 遍歷，目錄下的所有檔案
        for (File f : files) {
            if (f.isFile()) {
                fileList.add(f);

            } else if (f.isDirectory()) {
                System.out.println(f.getAbsolutePath());
                readFile(f.getAbsolutePath());
            }
        }
        for (File f1 : fileList) {
            System.out.println(f1.getName());
        }
    }





    //-------------------------------------------------------------------------------------------------------------



    private ObjectVendorExtension getContractProperty(String contractSource, String filepath_testXml, String appName) throws Exception{
        ObjectVendorExtension contract =  new ObjectVendorExtension("x-contract");

        String contractContent = "";
        contractContent = contractSource.substring(contractSource.indexOf("[") + 1, contractSource.lastIndexOf("]"));
        String [] part1 = contractContent.split("Contract.make");

        //ObjectVendorExtension oasSourcePath = newOrGetObjProperty(httpRequest.getValue(), sourcePath);
        for( int i = 1; i <= part1.length-1; i++ ) {

            ObjectVendorExtension url = this.getUrl(part1[i]);

            ObjectVendorExtension content = new ObjectVendorExtension("contractContent");
            StringVendorExtension description = this.getDescription(part1[i]);
            StringVendorExtension name = this.getName(part1[i]);
            ObjectVendorExtension request = this.getRequest(part1[i]);
            ObjectVendorExtension response = this.getResponse(part1[i]);

            ObjectVendorExtension test = new ObjectVendorExtension("testResult");
            StringVendorExtension started;
            StringVendorExtension finished;
            StringVendorExtension duration;
            StringVendorExtension status;

            url.addProperty(content);
            url.addProperty(test);

            if (url.getValue() != null) {
                if (description.getValue() != null) {
                    content.addProperty(description);
                }
                if (name.getValue() != null) {
                    content.addProperty(name);
                }
                if (request.getValue() != null) {
                    content.addProperty(request);
                }
                if (response.getValue() != null) {
                    content.addProperty(response);
                }

                contract.addProperty(url);
            }

            ArrayList<HashMap<String,String>> testXmlSource = readfile_testXml(filepath_testXml, appName);


            if(testXmlSource != null) {
                for (HashMap<String, String> h : testXmlSource) {

                    System.out.println("testMethodName: " + h.getOrDefault("name", "null"));

                    if (h.getOrDefault("name", "null").replaceFirst("validate_", "").equals(name.getValue().replaceAll("-","_"))) {
                        started = new StringVendorExtension("started-at", h.getOrDefault("started-at", "null"));
                        finished = new StringVendorExtension("finished-at", h.getOrDefault("finished-at", "null"));
                        duration = new StringVendorExtension("duration-ms", h.getOrDefault("duration-ms", "null"));
                        status = new StringVendorExtension("status", h.getOrDefault("status", "null"));

                        if (started.getValue() != null) {
                            test.addProperty(started);
                        }
                        if (finished.getValue() != null) {
                            test.addProperty(finished);
                        }
                        if (duration.getValue() != null) {
                            test.addProperty(duration);
                        }
                        if (status.getValue() != null) {
                            test.addProperty(status);
                        }

                        if(h.getOrDefault("status", "null").equals("FAIL")){
                            ObjectVendorExtension errorMessage = new ObjectVendorExtension("errorMessage");
                            StringVendorExtension exceptionType = new StringVendorExtension("exception", h.getOrDefault("exception", "null"));
                            StringVendorExtension message = new StringVendorExtension("message", h.getOrDefault("message", "null"));


                            test.addProperty(errorMessage);

                            if (exceptionType.getValue() != null) {
                                errorMessage.addProperty(exceptionType);
                            }
                            if (message.getValue() != null) {
                                errorMessage.addProperty(message);
                            }
                        }

                        break;
                    }
                }
            }
        }

        return contract;
    }

    private String readfile_groovy(String filepath) throws IOException {
        try {
            InputStream is = this.getClass().getResourceAsStream(filepath);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String s = "";
            StringBuilder sb = new StringBuilder("");
            while ((s = br.readLine()) != null)
                sb.append(s).append("\n");

            logger.info("Contract Source: " + "\n" + sb.toString());

            return sb.toString();

        }catch(Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private ArrayList<HashMap<String,String>> readfile_testXml(String filepath, String appName) throws IOException {


        ArrayList<HashMap<String,String>> al = new ArrayList<>();


        try {
            InputStream is = this.getClass().getResourceAsStream(filepath);

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);

            doc.getDocumentElement().normalize();
            logger.info("Root element :" + doc.getDocumentElement().getNodeName());
            //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

            NodeList nList = doc.getElementsByTagName("class");

            for( int i = 0; i < nList.getLength(); i++){
                Node iNode = nList.item(i);

                String tests = iNode.getAttributes().getNamedItem("name").getNodeValue();
                logger.info("testName :" + tests);

                if(tests.equals(appName + ".ContractVerifierTest")){
                    if (iNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element elem = (Element) iNode;
                        NodeList nl = elem.getElementsByTagName("test-method");
                        for( int j = 0; j < nl.getLength(); j++){
                            HashMap<String,String> hashMap = new HashMap<>();
                            Node jNode = nl.item(j);

                            hashMap.put("signature", jNode.getAttributes().getNamedItem("signature").getNodeValue());
                            hashMap.put("name", jNode.getAttributes().getNamedItem("name").getNodeValue());
                            hashMap.put("started-at", jNode.getAttributes().getNamedItem("started-at").getNodeValue());
                            hashMap.put("finished-at", jNode.getAttributes().getNamedItem("finished-at").getNodeValue());
                            hashMap.put("duration-ms", jNode.getAttributes().getNamedItem("duration-ms").getNodeValue());
                            hashMap.put("status", jNode.getAttributes().getNamedItem("status").getNodeValue());

                            logger.info("signature: " + hashMap.getOrDefault("signature","null"));
                            logger.info("name: " + hashMap.getOrDefault("name","null"));
                            logger.info("started-at: " + hashMap.getOrDefault("started-at","null"));
                            logger.info("finished-at: " + hashMap.getOrDefault("finished-at","null"));
                            logger.info("duration-ms: " + hashMap.getOrDefault("duration-ms","null"));
                            logger.info("status: " + hashMap.getOrDefault("status","null"));

                            // 假如是失敗的
                            if (hashMap.getOrDefault("status","null").equals("FAIL")){
                                Element elem2 = (Element) jNode;
                                NodeList nl2 = elem2.getElementsByTagName("exception");

                                // <exception>
                                Node node2 = nl2.item(0);
                                String exceptionType = node2.getAttributes().getNamedItem("class").getNodeValue();

                                // <message>
                                Element elem3 = (Element) iNode;
                                NodeList nl3 = elem.getElementsByTagName("message");
                                Node node3 = nl3.item(0);
//                                NodeList nl4 = elem.getElementsByTagName("full-stacktrace");

                                String errorMessage = node3.getTextContent().trim().replaceAll("\n","").replaceAll("\\\\","");

                                hashMap.put("exception", exceptionType);
                                hashMap.put("message", errorMessage);

                                logger.info("exception: " + hashMap.getOrDefault("exception","null"));
                                logger.info("message: " + hashMap.getOrDefault("message","null"));
                            }

                            al.add(hashMap);
                        }

                    }
                }
            }

            System.out.println("alssize: " + al.size());

            return al;

        }catch(Exception e){
            e.printStackTrace();
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private ObjectVendorExtension getUrl(String str) {

        String temp1 = str.substring(str.indexOf("request"));
        String temp2 = temp1.substring(temp1.indexOf("url"));
        String result = temp2.substring(temp2.indexOf("(") + 2, temp2.indexOf(")") - 1);

        return new ObjectVendorExtension(result);
    }

    private StringVendorExtension getDescription(String str) {

        String temp1 = str.substring(str.indexOf("description"));
        String result = temp1.substring(temp1.indexOf("(") + 2, temp1.indexOf(")") - 1);

        return new StringVendorExtension("description",result);
    }

    private StringVendorExtension getName(String str) {
        String temp1 = str.substring(str.indexOf("description"));
        String temp2 = temp1.substring(temp1.indexOf(")"));
        String temp3 = temp2.substring(temp2.indexOf("name"));
        String result = temp3.substring(temp3.indexOf("(") + 2, temp3.indexOf(")") - 1);

        return new StringVendorExtension("name",result);
    }

    private ObjectVendorExtension getRequest(String str) {
        ObjectVendorExtension resultRequest = new ObjectVendorExtension("request");

        // 取得request區塊
        String temp1 = str.substring(str.indexOf("description"));
        String temp2 = temp1.substring(temp1.indexOf(")"));
        String temp3 = temp2.substring(temp2.indexOf("name"));
        String temp4 = temp3.substring(temp3.indexOf(")"));
        String content = temp4.substring(temp4.indexOf("request"), temp4.indexOf("response") - 2).replaceAll("\n","").replaceAll(" ","");

        // 取得method
        String temp5 = content.substring(content.indexOf("method"));
        String method = temp5.substring(temp5.indexOf("(") + 2, temp5.indexOf(")") - 1);

        // 取得參數
        if(content.contains("queryParameters")) {
            String temp6 = content.substring(content.indexOf("queryParameters"));
            String[] parameters = temp6.split("parameter");
            ObjectVendorExtension queryParameters = new ObjectVendorExtension("queryParameters");
            for (int i = 1; i <= parameters.length - 1; i++) {
                String parameterName = parameters[i].substring(parameters[i].indexOf("(") + 2, parameters[i].indexOf(",") - 1);
                String parameterValue = parameters[i].substring(parameters[i].indexOf(",") + 2, parameters[i].indexOf(")") - 1);

                queryParameters.addProperty(new StringVendorExtension(parameterName, parameterValue));
            }

            resultRequest.addProperty( queryParameters );

        }


        resultRequest.addProperty( new StringVendorExtension("method",method) );

        return resultRequest;
    }

    private ObjectVendorExtension getResponse(String str) {
        ObjectVendorExtension resultResponse = new ObjectVendorExtension("response");


        String temp1 = str.substring(str.indexOf("response")).replaceAll("\n","").replaceAll(" ","");

        // 取得 body
        if(temp1.contains("body")) {
            String temp2 = temp1.substring(temp1.indexOf("body"));
            String body = temp2.substring(temp2.indexOf("(") + 2, temp2.indexOf(")") - 1);
            resultResponse.addProperty(new StringVendorExtension("body", body ));
        }

        //取得 status
        if(temp1.contains("status")) {
            String temp4 = temp1.substring(temp1.indexOf("status"));
            String status = temp4.substring(temp4.indexOf("(") + 1, temp4.indexOf(")"));
            resultResponse.addProperty(new StringVendorExtension("status", status ));
        }





        return resultResponse;
    }

}
