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
import springfox.documentation.service.ListVendorExtension;
import springfox.documentation.service.ObjectVendorExtension;
import springfox.documentation.service.VendorExtension;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class ServiceDependencyAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ServiceDependencyAnalyzer.class);
    private static final String GET = "get", POST = "post", PUT = "put", PATCH = "patch", DELETE = "delete";

    public List<VendorExtension> swaggerExtension(String basePackage) {
        Set<Class<?>> classes = getClasses(basePackage);
        ObjectVendorExtension extension = new ObjectVendorExtension("x-serviceDependency");
        ObjectVendorExtension httpRequest = getHttpRequestProperty(classes);
        ObjectVendorExtension amqp = getAmqpProperty(classes);
        if (httpRequest.getValue().size() != 0) {
            extension.addProperty(httpRequest);
        }
        if (amqp.getValue().size() != 0) {
            extension.addProperty(amqp);
        }

        return Collections.singletonList(extension);
    }

    // Return the property of "httpRequest". This property contain the info of http request sources and targets.
    private ObjectVendorExtension getHttpRequestProperty(Set<Class<?>> classes) {
        ObjectVendorExtension httpRequest =  new ObjectVendorExtension("httpRequest");
        for (Class c: classes) {
            if (c.isAnnotationPresent(RestController.class)) {
                String cPath = getControllerPath(c);
                for (Method m: c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(FeignRequests.class) || m.isAnnotationPresent(FeignRequest.class)) {
                        // Source
                        HashMap<String, String> mappingInfoMap = getInfoFromMapping(m);
                        String sourceMethod = mappingInfoMap.get("method");
                        String mPath = mappingInfoMap.get("path");
                        String sourcePath = cPath + mPath;
                        // Add source property to swagger
                        ObjectVendorExtension oasSourcePath = newOrGetObjProperty(httpRequest.getValue(), sourcePath);
                        ObjectVendorExtension oasSourceMethod = new ObjectVendorExtension(sourceMethod);
                        oasSourcePath.addProperty(oasSourceMethod);
                        httpRequest.addProperty(oasSourcePath);
                        logger.info("Http request source: " + "[" + oasSourceMethod.getName() + "]" + oasSourcePath.getName());
                        // Targets
                        ObjectVendorExtension oasTargets = new ObjectVendorExtension("targets");
                        oasSourceMethod.addProperty(oasTargets);
                        if (m.isAnnotationPresent(FeignRequests.class)) {
                            FeignRequest[] requests = m.getAnnotation(FeignRequests.class).value();
                            for (FeignRequest request : requests) {
                                HashMap<String, String> targetInfoMap = getTargetInfoFromFeingRequest(request);
                                addHttpTargetPropertyToTargets(oasTargets, targetInfoMap);
                                logger.info("Http request target: " + targetInfoMap.get("service") + ":" + targetInfoMap.get("version")
                                        + " - [" + targetInfoMap.get("method") + "]" + targetInfoMap.get("path"));
                            }
                        } else if (m.isAnnotationPresent(FeignRequest.class)) {
                            FeignRequest request = m.getAnnotation(FeignRequest.class);
                            HashMap<String, String> targetInfoMap = getTargetInfoFromFeingRequest(request);
                            addHttpTargetPropertyToTargets(oasTargets, targetInfoMap);
                            logger.info("Http request target: " + targetInfoMap.get("service") + ":" + targetInfoMap.get("version")
                                    + " - [" + targetInfoMap.get("method") + "]" + targetInfoMap.get("path"));
                        }
                    }
                }
            } else if (c.isAnnotationPresent(SpringBootApplication.class)
                    && (c.isAnnotationPresent(FeignRequest.class))
                    || c.isAnnotationPresent(FeignRequests.class)) {
                String sourcePath = "none";
                // Add source property to swagger
                ObjectVendorExtension oasSourcePath = newOrGetObjProperty(httpRequest.getValue(), sourcePath);
                logger.info("Http request source: " + sourcePath);
                // Targets
                ObjectVendorExtension oasTargets = new ObjectVendorExtension("targets");
                if (c.isAnnotationPresent(FeignRequests.class)) {
                    FeignRequest[] requests = null;
                    Annotation requestsAnno = c.getAnnotation(FeignRequests.class);
                    Method[] methods = requestsAnno.annotationType().getDeclaredMethods();
                    for (Method method: methods) {
                        try {
                            if (method.getName().equals("value")) {
                                requests = (FeignRequest[]) method.invoke(requestsAnno, (Object[]) null);
                                break;
                            }
                        } catch (IllegalAccessException e) {
                            logger.error(e.getMessage(), e);
                        } catch (InvocationTargetException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    for (FeignRequest request : requests) {
                        HashMap<String, String> targetInfoMap = getTargetInfoFromFeingRequest(request);
                        addHttpTargetPropertyToTargets(oasTargets, targetInfoMap);
                        logger.info("Http request target: " + targetInfoMap.get("service") + ":" + targetInfoMap.get("version")
                                + " - [" + targetInfoMap.get("method") + "]" + targetInfoMap.get("path"));
                    }
                } else if (c.isAnnotationPresent(FeignRequest.class)) {
                    Annotation request = c.getAnnotation(FeignRequest.class);
                    HashMap<String, String> targetInfoMap = getTargetInfoFromFeingRequest(request);
                    addHttpTargetPropertyToTargets(oasTargets, targetInfoMap);
                    logger.info("Http request target: " + targetInfoMap.get("service") + ":" + targetInfoMap.get("version")
                            + " - [" + targetInfoMap.get("method") + "]" + targetInfoMap.get("path"));
                }
                oasSourcePath.addProperty(oasTargets);
                httpRequest.addProperty(oasSourcePath);
            }
        }

        return httpRequest;
    }

    // Return the property of "amqp". This property contain the info of publish and subscribe with sources and targets.
    private ObjectVendorExtension getAmqpProperty(Set<Class<?>> classes) {
        ObjectVendorExtension amqp =  new ObjectVendorExtension("amqp");
        ObjectVendorExtension publish = new ObjectVendorExtension("publish");
        ObjectVendorExtension subscribe = new ObjectVendorExtension("subscribe");
        // Publish
        for (Class c: classes) {
            if (c.isAnnotationPresent(RestController.class)) {
                String cPath = getControllerPath(c);
                for (Method m: c.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(RabbitPublishers.class) || m.isAnnotationPresent(RabbitPublisher.class)) {
                        // Source
                        HashMap<String, String> mappingInfoMap = getInfoFromMapping(m);
                        String sourceMethod = mappingInfoMap.get("method");
                        String mPath = mappingInfoMap.get("path");
                        String sourcePath = cPath + mPath;
                        // Add source property to swagger
                        ObjectVendorExtension oasSourcePath = newOrGetObjProperty(publish.getValue(), sourcePath);
                        ObjectVendorExtension oasSourceMethod = new ObjectVendorExtension(sourceMethod);
                        oasSourcePath.addProperty(oasSourceMethod);
                        logger.info("AMQP source: " + "[" + oasSourceMethod.getName() + "]" + oasSourcePath.getName());
                        // Targets
                        if (m.isAnnotationPresent(RabbitPublishers.class)) {
                            publish.addProperty(oasSourcePath);
                            RabbitPublisher[] rabbitPublishers = m.getAnnotation(RabbitPublishers.class).value();
                            for (RabbitPublisher rabbitPublisher : rabbitPublishers) {
                                addAmqpTargetPropertyToTargets(oasSourceMethod, rabbitPublisher.value());
                            }
                        } else if (m.isAnnotationPresent(RabbitPublisher.class)) {
                            publish.addProperty(oasSourcePath);
                            RabbitPublisher rabbitPublisher = m.getAnnotation(RabbitPublisher.class);
                            addAmqpTargetPropertyToTargets(oasSourceMethod, rabbitPublisher.value());
                        }
                    }
                }
            } else if (c.isAnnotationPresent(SpringBootApplication.class)
                    && (c.isAnnotationPresent(RabbitPublisher.class)
                    || c.isAnnotationPresent(RabbitPublishers.class))) {
                String sourcePath = "none";
                // Add source property to swagger
                ObjectVendorExtension oasSourcePath = newOrGetObjProperty(publish.getValue(), sourcePath);
                publish.addProperty(oasSourcePath);
                logger.info("AMQP source: " + sourcePath);
                if (c.isAnnotationPresent(RabbitPublishers.class)) {
                    RabbitPublisher[] rabbitPublishers = null;
                    Annotation amqpSendersAnno = c.getAnnotation(RabbitPublishers.class);
                    Method[] methods = amqpSendersAnno.annotationType().getDeclaredMethods();
                    for (Method method: methods) {
                        try {
                            if (method.getName().equals("value")) {
                                rabbitPublishers = (RabbitPublisher[]) method.invoke(amqpSendersAnno, (Object[]) null);
                                break;
                            }
                        } catch (IllegalAccessException e) {
                            logger.error(e.getMessage(), e);
                        } catch (InvocationTargetException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    for (RabbitPublisher rabbitPublisher : rabbitPublishers) {
                        addAmqpTargetPropertyToTargets(oasSourcePath, rabbitPublisher.value());
                    }
                } else if (c.isAnnotationPresent(RabbitPublisher.class)) {
                    Annotation amqpSenderAnno = c.getAnnotation(RabbitPublisher.class);
                    Method[] methods = amqpSenderAnno.annotationType().getDeclaredMethods();
                    for (Method method: methods) {
                        try {
                            if (method.getName().equals("value")) {
                                String queue = (String) method.invoke(amqpSenderAnno, (Object[]) null);
                                addAmqpTargetPropertyToTargets(oasSourcePath, queue);
                                break;
                            }
                        } catch (IllegalAccessException e) {
                            logger.error(e.getMessage(), e);
                        } catch (InvocationTargetException e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                }
            }
        }
        // Subscribe
        for (Class c: classes) {
            String cPath = c.isAnnotationPresent(RestController.class) ? getControllerPath(c) : "";
            if (c.isAnnotationPresent(RabbitListener.class)) {
                ObjectVendorExtension oasSourcePath = newOrGetObjProperty(subscribe.getValue(), "none");
                subscribe.addProperty(oasSourcePath);
                Annotation rabbitListenerAnno = c.getAnnotation(RabbitListener.class);
                Method[] methods = rabbitListenerAnno.annotationType().getDeclaredMethods();
                for (Method method: methods) {
                    try {
                        if (method.getName().equals("queues")) {
                            String[] queues = (String[]) method.invoke(rabbitListenerAnno, (Object[]) null);
                            for (String queue: queues) {
                                addAmqpTargetPropertyToTargets(oasSourcePath, queue);
                            }
                            break;
                        }
                    } catch (IllegalAccessException e) {
                        logger.error(e.getMessage(), e);
                    } catch (InvocationTargetException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
            for (Method m: c.getDeclaredMethods()) {
                if (m.isAnnotationPresent(RabbitListener.class)) {
                    if (m.isAnnotationPresent(RequestMapping.class)
                    || m.isAnnotationPresent(GetMapping.class)
                    || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(PutMapping.class)
                    || m.isAnnotationPresent(PatchMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class)) {
                        HashMap<String, String> sourceInfoMap = getInfoFromMapping(m);
                        ObjectVendorExtension oasSourcePath = newOrGetObjProperty(subscribe.getValue(), cPath + sourceInfoMap.get("path"));
                        subscribe.addProperty(oasSourcePath);
                        ObjectVendorExtension oasSourceMethod = new ObjectVendorExtension(sourceInfoMap.get("method"));
                        oasSourcePath.addProperty(oasSourceMethod);
                        RabbitListener rabbitListener = m.getAnnotation(RabbitListener.class);
                        String[] queues = rabbitListener.queues();
                        for (String queue: queues) {
                            addAmqpTargetPropertyToTargets(oasSourceMethod, queue);
                        }
                    } else {
                        ObjectVendorExtension oasSourcePath = newOrGetObjProperty(subscribe.getValue(), "none");
                        subscribe.addProperty(oasSourcePath);
                        RabbitListener rabbitListener = m.getAnnotation(RabbitListener.class);
                        String[] queues = rabbitListener.queues();
                        for (String queue: queues) {
                            addAmqpTargetPropertyToTargets(oasSourcePath, queue);
                        }
                    }
                }
            }
        }
        if (publish.getValue().size() != 0) {
            amqp.addProperty(publish);
        }
        if (subscribe.getValue().size() != 0) {
            amqp.addProperty(subscribe);
        }

        return amqp;
    }

    // Get controller path from @RequestMapping
    private String getControllerPath(Class<?> c) {

        if (c.isAnnotationPresent(RequestMapping.class)) {
            String path = null;
            Annotation controllerMapping = c.getAnnotation(RequestMapping.class);
            Method[] methods = controllerMapping.annotationType().getDeclaredMethods();
            for (Method method : methods) {
                try {
                    if (method.getName().equals("value")) {
                        String cValue = Arrays.toString((String[])method.invoke(controllerMapping, (Object[]) null));
                        path = (cValue.substring(cValue.indexOf("[") + 1, cValue.lastIndexOf("]")));
                        path = (path.charAt(0) + "").equals("/") ? path : "/" + path;
                    }
                } catch (IllegalAccessException e) {
                    logger.error(e.getMessage(), e);
                } catch (InvocationTargetException e) {
                    logger.error(e.getMessage(), e);
                }
            }
            return path;
        } else {
            return "";
        }

    }

    // Return the pure value string from @RequestMapping.value() (including @GetMapping, @PostMapping, and so on.)
    private String mappingValueToString(Object[] strs) {
        String path = Arrays.toString(strs);
        path = path.substring(path.indexOf("[") + 1, path.lastIndexOf("]"));
        path = (path.charAt(0) + "").equals("/") ? path : "/" + path;

        return path;
    }

    // Return the pure method string from @RequestMapping.method() (including @GetMapping, @PostMapping, and so on.)
    private String mappingMethodToString(Object[] strs) {
        String method = Arrays.toString(strs);
        method = method.substring(method.indexOf("[") + 1, method.lastIndexOf("]")).toLowerCase();

        return method;
    }

    // Return mapping info from method's mapping annotation. Using "method", "path" as key to get value.
    private HashMap<String, String> getInfoFromMapping(Method method) {
        HashMap<String, String> sourceInfoMap = new HashMap<>();
        if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            sourceInfoMap.put("method", mappingMethodToString(mapping.method()));
            sourceInfoMap.put("path", mappingValueToString(mapping.value()));
        } else if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            sourceInfoMap.put("method", GET);
            sourceInfoMap.put("path", mappingValueToString(mapping.value()));
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            sourceInfoMap.put("method", POST);
            sourceInfoMap.put("path", mappingValueToString(mapping.value()));
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            sourceInfoMap.put("method", PUT);
            sourceInfoMap.put("path", mappingValueToString(mapping.value()));
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            PatchMapping mapping = method.getAnnotation(PatchMapping.class);
            sourceInfoMap.put("method", PATCH);
            sourceInfoMap.put("path", mappingValueToString(mapping.value()));
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            sourceInfoMap.put("method", DELETE);
            sourceInfoMap.put("path", mappingValueToString(mapping.value()));
        }

        return sourceInfoMap;
    }

    // Return target info from @FeignRequest. Using "service", "version", "method", "path" as key to get value.
    private HashMap<String, String> getTargetInfoFromFeingRequest(FeignRequest request) {
        HashMap<String, String> targetInfoMap = new HashMap<>();
        Class<?> feignClient = request.client();
        if (feignClient.isInterface() && feignClient.isAnnotationPresent(FeignClient.class)) {
            Method feignMethod = null;
            try {
                feignMethod = feignClient.getMethod(request.method(), request.parameterTypes());
            } catch (NoSuchMethodException e) {
                logger.error(e.getMessage(), e);
            }
            String targetService = feignClient.getAnnotation(FeignClient.class).value();
            String targetVersion = "notSpecified";
            String targetMethod = null;
            String targetPath = null;
            if (feignClient.isAnnotationPresent(TargetVersion.class)) {
                targetVersion = feignClient.getAnnotation(TargetVersion.class).value();
            }
            if (feignMethod != null) {
                HashMap<String, String> targetMappingInfo = getInfoFromMapping(feignMethod);
                targetMethod = targetMappingInfo.get("method");
                targetPath = targetMappingInfo.get("path");
            }
            targetInfoMap.put("service", targetService);
            targetInfoMap.put("version", targetVersion);
            targetInfoMap.put("method", targetMethod);
            targetInfoMap.put("path", targetPath);
        }

        return targetInfoMap;
    }

    // Return target info. Using "service", "version", "method", "path" as key to get value.
    private HashMap<String, String> getTargetInfoFromFeingRequest(Annotation request) {
        HashMap<String, String> targetInfoMap = new HashMap<>();
        Method[] methods = request.annotationType().getDeclaredMethods();
        Class<?> feignClient = null;
        String feignMethodName = null;
        Class<?>[] feignParameterTypes = null;
        for (Method method: methods) {
            try {
                if (method.getName().equals("client")) {
                    feignClient = (Class<?>) method.invoke(request, (Object[]) null);
                } else if (method.getName().equals("method")) {
                    feignMethodName = (String) method.invoke(request, (Object[]) null);
                } else if (method.getName().equals("parameterTypes")) {
                    feignParameterTypes = (Class<?>[]) method.invoke(request, (Object[]) null);
                }
            } catch (IllegalAccessException e) {
                logger.error(e.getMessage(), e);
            } catch (InvocationTargetException e) {
                logger.error(e.getMessage(), e);
            }
        }

        if (feignClient.isInterface() && feignClient.isAnnotationPresent(FeignClient.class)) {
            Method feignMethod = null;
            try {
                feignMethod = feignClient.getMethod(feignMethodName, feignParameterTypes);
            } catch (NoSuchMethodException e) {
                logger.error(e.getMessage(), e);
            }
            String targetService = feignClient.getAnnotation(FeignClient.class).value();
            String targetVersion = "notSpecified";
            String targetMethod = null;
            String targetPath = null;
            if (feignClient.isAnnotationPresent(TargetVersion.class)) {
                targetVersion = feignClient.getAnnotation(TargetVersion.class).value();
            }
            if (feignMethod != null) {
                HashMap<String, String> targetMappingInfo = getInfoFromMapping(feignMethod);
                targetMethod = targetMappingInfo.get("method");
                targetPath = targetMappingInfo.get("path");
            }
            targetInfoMap.put("service", targetService);
            targetInfoMap.put("version", targetVersion);
            targetInfoMap.put("method", targetMethod);
            targetInfoMap.put("path", targetPath);
        }

        return targetInfoMap;
    }

    // If the property is exist in List<VendorExtension> properties, return old property, otherwise, return new ObjectVendorExtension property.
    private ObjectVendorExtension newOrGetObjProperty(List<VendorExtension> properties, String name) {
        boolean exist = false;
        ObjectVendorExtension resultProperty = null;
        for (VendorExtension property: properties) {
            if (property.getName().equals(name)) {
                exist = true;
                resultProperty = (ObjectVendorExtension) property;
                break;
            }
        }
        if (!exist) {
            resultProperty = new ObjectVendorExtension(name);
        }

        return resultProperty;
    }

    // If the property is exist in List<VendorExtension> properties, return old property, otherwise, return new ListVendorExtension property.
    private ListVendorExtension newOrGetListProperty(List<VendorExtension> properties, String name) {
        boolean exist = false;
        ListVendorExtension resultProperty = null;
        for (VendorExtension property: properties) {
            if (property.getName().equals(name)) {
                exist = true;
                resultProperty = (ListVendorExtension) property;
                break;
            }
        }
        if (!exist) {
            resultProperty = new ListVendorExtension<>(name, new ArrayList<>());
        }

        return resultProperty;
    }

    // Add HTTP target info to oasTargets property.
    // targetInfoMap should contains "service", "version", "method", "path". It can be created by using getTargetInfoFromFeingRequest().
    private void addHttpTargetPropertyToTargets(ObjectVendorExtension oasTargets, HashMap<String, String> targetInfoMap) {
        ObjectVendorExtension oasTargetService = newOrGetObjProperty(oasTargets.getValue(), targetInfoMap.get("service"));
        ObjectVendorExtension oasTargetVersion = newOrGetObjProperty(oasTargetService.getValue(), targetInfoMap.get("version"));
        oasTargetService.addProperty(oasTargetVersion);
        ListVendorExtension oasTargetPath = newOrGetListProperty(oasTargetVersion.getValue(), targetInfoMap.get("path"));
        List<String> oasTargetMethods = new ArrayList<String>(oasTargetPath.getValue());
        oasTargetMethods.add(targetInfoMap.get("method"));
        oasTargetPath = new ListVendorExtension(targetInfoMap.get("path"), oasTargetMethods);
        oasTargetVersion.replaceProperty(oasTargetPath);
        oasTargets.addProperty(oasTargetService);
    }

    // Add AMQP target queue to oasTargetParent's targets property.
    private void addAmqpTargetPropertyToTargets(ObjectVendorExtension oasTargetParent, String queue) {
        ListVendorExtension oasTargets = newOrGetListProperty(oasTargetParent.getValue(), "targets");
        List<String> oasTargetOueues = new ArrayList<>(oasTargets.getValue());
        boolean exist = false;
        for (String targetQueue: oasTargetOueues) {
            if (targetQueue.equals(queue)) {
                exist = true;
                break;
            }
        }
        if (!exist) {
            oasTargetOueues.add(queue);
            oasTargets = new ListVendorExtension("targets", oasTargetOueues);
            oasTargetParent.replaceProperty(oasTargets);
        }
    }

    // Get classes under package.
    private Set<Class<?>> getClasses(String basePackage) {
        Reflections reflections = new Reflections(basePackage, new SubTypesScanner(false));
        Set<Class<?>> classes = reflections.getSubTypesOf(Object.class);

        return classes;
    }

}
