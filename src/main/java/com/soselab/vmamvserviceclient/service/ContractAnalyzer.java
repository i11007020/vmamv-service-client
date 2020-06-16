package com.soselab.vmamvserviceclient.service;

import com.soselab.vmamvserviceclient.annotation.*;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.QueryParameter;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierObjectMapper;
import org.springframework.cloud.contract.verifier.util.ContractVerifierDslConverter;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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
import java.util.*;

public class ContractAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ContractAnalyzer.class);
    private static final String GET = "get", POST = "post", PUT = "put", PATCH = "patch", DELETE = "delete";

    public List<VendorExtension> swaggerExtension(String filepath_groovy, String filepath_testXml, String appName) throws Exception {
        ArrayList<String> contractFileName = new ArrayList<>();
        //ArrayList<String> mappingSource = new ArrayList<>();

        contractFileName = readFile_dir(filepath_groovy);
        //mappingSource = readFile_dir(filepath_mappings);


        ObjectVendorExtension extension;

        if(contractFileName == null || contractFileName.size() == 0) {
            extension = new ObjectVendorExtension("x-contract");
        } else {
            ArrayList<HashMap<String,String>> groovys = new ArrayList<>();

            for(String c: contractFileName){
                HashMap<String,String> groovy = new HashMap<String,String>();
                groovy.put("fileName", c);
                groovy.put("fileContent", getfileContent_groovy(filepath_groovy + c));
                groovys.add(groovy);
            }

            extension = getContractProperty(groovys, filepath_testXml, appName);
        }
        return Collections.singletonList(extension);
    }




    // 讀取jar檔某目錄下的所有檔案
    public ArrayList<String> readFile_dir(String fileDir) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:" + fileDir + "*.*");

            ArrayList<String> files = new ArrayList<>();

            logger.info(fileDir + ": ");

            for (Resource resource : resources) {

                files.add(resource.getFilename());
                logger.info(resource.getFilename());

            }

            return files;
        }catch(Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
            return null;
        }

    }





    //-------------------------------------------------------------------------------------------------------------



    private ObjectVendorExtension getContractProperty(ArrayList<HashMap<String,String>> groovys, String filepath_testXml, String appName) throws Exception{
        ObjectVendorExtension contract =  new ObjectVendorExtension("x-contract");


        for(HashMap<String,String> groovy: groovys){
            String fileName = groovy.get("fileName");
            String fileContent = groovy.get("fileContent");

            ObjectVendorExtension  fileNameExtension =  new ObjectVendorExtension(fileName);
            contract.addProperty(fileNameExtension);


            String contractContent = "";
            contractContent = fileContent.substring(fileContent.indexOf("[") + 1, fileContent.lastIndexOf("]"));
            String [] part1 = contractContent.split("Contract.make");

            for( int i = 1; i <= part1.length; i++ ) {

                part1[i] = part1[i].trim();

                if(part1[i].endsWith(","))
                    part1[i] = part1[i].substring(0,part1[i].length()-1);

                logger.info("Collection<Contract>_" + fileName + ": ");
                logger.info("Contract Content_" + i + ": " + "import org.springframework.cloud.contract.spec.Contract\n" + "[\n" + "Contract.make" + part1[i] + "\n]");
                Collection<Contract> collectionContract = ContractVerifierDslConverter.convertAsCollection("import org.springframework.cloud.contract.spec.Contract\n" + "[\n" + "Contract.make" + part1[i] + "\n]");
                logger.info("collectionContract_" + i + ": " + collectionContract);

                if(collectionContract.iterator().hasNext()) {
                    Contract ct = collectionContract.iterator().next();

                    ObjectVendorExtension url = new ObjectVendorExtension(ct.getRequest().getUrl().getClientValue().toString());
                    ObjectVendorExtension content = new ObjectVendorExtension("contractContent");
                    StringVendorExtension ignored = new StringVendorExtension("ignored", String.valueOf(ct.getIgnored()));
                    StringVendorExtension description = new StringVendorExtension("desciption", ct.getDescription());
                    StringVendorExtension name = new StringVendorExtension("name", ct.getName());

                    ObjectVendorExtension request = this.getRequest(ct);
                    ObjectVendorExtension response = this.getResponse(ct);

                    ObjectVendorExtension test = new ObjectVendorExtension("testResult");
                    StringVendorExtension started;
                    StringVendorExtension finished;
                    StringVendorExtension duration;
                    StringVendorExtension status;

                    url.addProperty(content);
                    url.addProperty(test);

                    if (url.getValue() != null) {
                        if (ignored.getValue() != null) {
                            content.addProperty(ignored);
                        }
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

                        fileNameExtension.addProperty(url);
                    }

                    ArrayList<HashMap<String, String>> testXmlSource = readfile_testXml(filepath_testXml, appName);


                    if (testXmlSource != null) {
                        for (HashMap<String, String> h : testXmlSource) {

                            System.out.println("testMethodName: " + h.getOrDefault("name", "null"));

                            if (h.getOrDefault("name", "null").toLowerCase().replaceFirst("validate_", "").equals(name.getValue().toLowerCase().replaceAll("-", "_"))) {
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

                                if (h.getOrDefault("status", "null").equals("FAIL")) {
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
            }
        }



        return contract;
    }

    private String getfileContent_groovy(String filepath) throws IOException {
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


    private ObjectVendorExtension getRequest(Contract ct) {
        ObjectVendorExtension resultRequest = new ObjectVendorExtension("request");

        if (ct.getRequest().getMethod() != null)
            resultRequest.addProperty( new StringVendorExtension("method", ct.getRequest().getMethod().getClientValue().toString()) );

        if (ct.getRequest().getUrl() != null) {
            if (ct.getRequest().getUrl().getQueryParameters() != null) {
                ObjectVendorExtension queryParameters = new ObjectVendorExtension("queryParameters");
                List<QueryParameter> qps = ct.getRequest().getUrl().getQueryParameters().getParameters();

                for(QueryParameter qp : qps)
                    queryParameters.addProperty(new StringVendorExtension(qp.getName(), qp.getClientValue().toString()));

                resultRequest.addProperty(queryParameters);
            }
        }

        if (ct.getRequest().getHeaders() != null)
            resultRequest.addProperty( new StringVendorExtension("header", ct.getRequest().getHeaders().toString()) );


        return resultRequest;
    }

    private ObjectVendorExtension getResponse(Contract ct) {
        ObjectVendorExtension resultResponse = new ObjectVendorExtension("response");

        if (ct.getResponse().getBody() != null)
            resultResponse.addProperty( new StringVendorExtension("body", ct.getResponse().getBody().getClientValue().toString()) );

        if (ct.getResponse().getStatus() != null)
            resultResponse.addProperty( new StringVendorExtension("status", ct.getResponse().getStatus().getClientValue().toString()) );

        if(ct.getResponse().getHeaders() != null)
            resultResponse.addProperty( new StringVendorExtension("header", ct.getResponse().getHeaders().toString() ));



        return resultResponse;
    }

}
