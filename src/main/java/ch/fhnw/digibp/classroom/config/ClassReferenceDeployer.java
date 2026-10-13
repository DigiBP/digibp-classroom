package ch.fhnw.digibp.classroom.config;

import org.cibseven.bpm.engine.ProcessEngineException;
import org.cibseven.bpm.engine.impl.bpmn.deployer.BpmnDeployer;
import org.cibseven.bpm.engine.impl.cmmn.deployer.CmmnDeployer;
import org.cibseven.bpm.engine.impl.persistence.deploy.Deployer;
import org.cibseven.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.cibseven.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.cibseven.bpm.engine.impl.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

/** Check model-selected Java classes only when creating a deployment. */
final class ClassReferenceDeployer implements Deployer {
    private static final Set<String> EXTENSION_NAMESPACES = Set.of(
            "http://camunda.org/schema/1.0/bpmn", "http://camunda.org/schema/1.0/cmmn",
            "http://activiti.org/bpmn",
            "http://cibseven.org/schema/1.0/bpmn", "http://cibseven.org/schema/1.0/cmmn",
            "http://cibseven.com/schema/1.0/bpmn", "http://cibseven.com/schema/1.0/cmmn");
    private final Predicate<String> hasScriptAccess;

    ClassReferenceDeployer(Predicate<String> hasScriptAccess) {
        this.hasScriptAccess = hasScriptAccess;
    }

    @Override
    public void deploy(DeploymentEntity deployment) {
        // Existing deployments keep their approval when definitions are reloaded into the cache.
        if (!deployment.isNew() || hasScriptAccess.test(deployment.getTenantId())) {
            return;
        }
        for (ResourceEntity resource : deployment.getResources().values()) {
            String name = resource.getName();
            if (Arrays.stream(BpmnDeployer.BPMN_RESOURCE_SUFFIXES).anyMatch(name::endsWith)
                    || Arrays.stream(CmmnDeployer.CMMN_RESOURCE_SUFFIXES).anyMatch(name::endsWith)) {
                checkResource(resource);
            }
        }
    }

    private void checkResource(ResourceEntity resource) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            parser.parse(new ByteArrayInputStream(resource.getBytes()), new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes)
                        throws SAXException {
                    for (int i = 0; i < attributes.getLength(); i++) {
                        String attributeUri = attributes.getURI(i);
                        // Listener attributes can be unqualified on an extension element.
                        if (("class".equals(attributes.getLocalName(i))
                                || "formHandlerClass".equals(attributes.getLocalName(i)))
                                && (EXTENSION_NAMESPACES.contains(attributeUri)
                                || (attributeUri.isEmpty() && EXTENSION_NAMESPACES.contains(uri)))) {
                            throw new SAXException("Direct Java class references are disabled (element '"
                                    + qName + "'). Use delegateExpression with an allowed bean instead.");
                        }
                    }
                    if (EXTENSION_NAMESPACES.contains(uri) && "constraint".equals(localName)
                            && "validator".equals(attribute(attributes, "name"))) {
                        String config = attribute(attributes, "config");
                        if (config != null && !config.isEmpty() && !StringUtil.isExpression(config)) {
                            throw new SAXException("Java classes as form validators are disabled. "
                                    + "Use an expression with an allowed bean instead.");
                        }
                    }
                }
            });
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ProcessEngineException("Cannot deploy resource '" + resource.getName() + "': "
                    + e.getMessage(), e);
        }
    }

    private static String attribute(Attributes attributes, String name) {
        for (int i = 0; i < attributes.getLength(); i++) {
            if (name.equals(attributes.getLocalName(i)) && (attributes.getURI(i).isEmpty()
                    || EXTENSION_NAMESPACES.contains(attributes.getURI(i)))) {
                return attributes.getValue(i);
            }
        }
        return null;
    }
}
