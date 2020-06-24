package com.soselab.vmamvserviceclient.service;

import com.soselab.vmamvserviceclient.contract.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.QueryParameter;
import org.springframework.cloud.contract.verifier.util.ContractVerifierDslConverter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
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

        contractFileName = readFile_dir(filepath_groovy);


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


        // groovys:服務上的所有契約，groovy:每份契約
        for(HashMap<String,String> groovy: groovys){
            String fileName = groovy.get("fileName");
            String fileContent = groovy.get("fileContent");

            ObjectVendorExtension  fileNameExtension =  new ObjectVendorExtension(fileName);
            contract.addProperty(fileNameExtension);



            String contractContent = "";
            contractContent = fileContent.substring(fileContent.indexOf("[") + 1, fileContent.lastIndexOf("]"));
            String [] part1 = contractContent.split("Contract.make");

            // <端點名, 契約> ， 每個端點可能有複數測試案例
            Map<String,ArrayList<API>> allAPIMap = new HashMap<>();


            // 每個測試案例
            for( int i = 1; i < part1.length; i++ ) {
                part1[i] = part1[i].trim();

                if(part1[i].endsWith(","))
                    part1[i] = part1[i].substring(0,part1[i].length()-1);

                logger.info("Collection<Contract>_" + fileName + ": ");
                logger.info("Contract Content_" + i + ": " + "import org.springframework.cloud.contract.spec.Contract\n" + "[\n" + "Contract.make" + part1[i] + "\n]");
                Collection<Contract> collectionContract = ContractVerifierDslConverter.convertAsCollection("import org.springframework.cloud.contract.spec.Contract\n" + "[\n" + "Contract.make" + part1[i] + "\n]");
                logger.info("collectionContract_" + i + ": " + collectionContract);

                if(collectionContract.iterator().hasNext()) {
                    Contract ct = collectionContract.iterator().next();

                    ArrayList<API> apis = allAPIMap.getOrDefault(ct.getRequest().getUrl().getClientValue().toString(), new ArrayList<API>());
                    API api = new API();

                    ContractContent cc = new ContractContent();

                    cc.setDescription(ct.getDescription());
                    cc.setName(ct.getName());
                    cc.setIgnored(ct.getIgnored());

                    cc.setRequest(getRequest(ct));
                    cc.setResponse(getResponse(ct));


                    TestResult tr = new TestResult();
                    ErrorMessage errorMessage = new ErrorMessage();


                    // 拿取測試結果
                    ArrayList<HashMap<String, String>> testXmlSource = readfile_testXml(filepath_testXml, appName);

                    if (testXmlSource != null) {
                        for (HashMap<String, String> h : testXmlSource) {

                            System.out.println("testMethodName: " + h.getOrDefault("name", "null"));

                            System.out.println("name.getValue().toLowerCase().replaceAll(\"-\", \"_\"): " + ct.getName().toLowerCase().replaceAll("-", "_"));
                            System.out.println("h.getOrDefault(\"name\", \"null\").toLowerCase().replaceFirst(\"validate_\", \"\"): " + h.getOrDefault("name", "null").toLowerCase().replaceFirst("validate_", ""));

                            if (h.getOrDefault("name", "null").toLowerCase().replaceFirst("validate_", "").equals(ct.getName().toLowerCase().replaceAll("-", "_"))) {

                                tr.setStarted_at(h.getOrDefault("started-at", "null"));
                                tr.setFinished_at(h.getOrDefault("finished-at", "null"));
                                tr.setDuration_ms(h.getOrDefault("duration-ms", "null"));
                                tr.setStatus(h.getOrDefault("status", "null"));


                                if (h.getOrDefault("status", "null").equals("FAIL")) {

                                    errorMessage.setException(h.getOrDefault("exception", "null"));
                                    errorMessage.setMessage(h.getOrDefault("message", "null"));

                                    tr.setErrorMessage(errorMessage);
                                }

                                break;
                            }
                        }
                    }

                    api.setContractContent(cc);
                    api.setTestResult(tr);

                    apis.add(api);


                    if(allAPIMap.get(ct.getRequest().getUrl().getClientValue().toString()) != null)
                        allAPIMap.replace(ct.getRequest().getUrl().getClientValue().toString(), apis);
                    else
                        allAPIMap.put(ct.getRequest().getUrl().getClientValue().toString(), apis);
                }
            }


            for (Map.Entry<String,ArrayList<API>> entry : allAPIMap.entrySet()) {
                String key = entry.getKey();
                ArrayList<API> value = entry.getValue();

                ListVendorExtension<API> url = new ListVendorExtension<>(key,value);

                fileNameExtension.addProperty(url);
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


    private Request getRequest(Contract ct){
        Request rq = new Request();

        if (ct.getRequest().getMethod() != null)
            rq.setMethod(ct.getRequest().getMethod().getClientValue().toString());

        if (ct.getRequest().getUrl() != null) {
            if (ct.getRequest().getUrl().getQueryParameters() != null) {
                List<QueryParameter> qps = ct.getRequest().getUrl().getQueryParameters().getParameters();
                HashMap<String,String> queryParameters = new HashMap<>();

                for(QueryParameter qp : qps)
                    queryParameters.put(qp.getName(), qp.getClientValue().toString());

                rq.setQueryParameters(queryParameters);

            }
        }


        if (ct.getRequest().getHeaders() != null)
            rq.setHeader(ct.getRequest().getHeaders().toString());


        return rq;
    }

    private Response getResponse(Contract ct) {
        Response rs = new Response();

        if (ct.getResponse().getBody() != null)
            rs.setBody(ct.getResponse().getBody().getClientValue().toString());

        if (ct.getResponse().getStatus() != null)
            rs.setStatus(ct.getResponse().getStatus().getClientValue().toString());

        if(ct.getResponse().getHeaders() != null)
            rs.setHeader(ct.getResponse().getHeaders().toString());

        return rs;
    }

}
