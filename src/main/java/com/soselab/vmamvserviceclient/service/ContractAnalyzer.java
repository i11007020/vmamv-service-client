package com.soselab.vmamvserviceclient.service;

import com.soselab.vmamvserviceclient.annotation.*;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import springfox.documentation.service.ListVendorExtension;
import springfox.documentation.service.ObjectVendorExtension;
import springfox.documentation.service.VendorExtension;
import springfox.documentation.service.StringVendorExtension;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.io.*;

public class ContractAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ContractAnalyzer.class);
    private static final String GET = "get", POST = "post", PUT = "put", PATCH = "patch", DELETE = "delete";

    public List<VendorExtension> swaggerExtension(String filepath_groovy, String filepath_testXml, String appName) throws Exception {
        String contractSource = readfile_groovy(filepath_groovy);


        ObjectVendorExtension extension;

        if(contractSource == null || contractSource.equals("")) {
            extension = new ObjectVendorExtension("x-contract");
        } else {
                extension = getContractProperty(contractSource, filepath_testXml, appName);
        }
        return Collections.singletonList(extension);
    }


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

                    System.out.println("hhhhhh: " + h.getOrDefault("name", "null"));

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
