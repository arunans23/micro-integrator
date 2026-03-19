/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.dataservices.core.graphql;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses XML output from a DataService query execution into
 * a Map/List structure that graphql-java can use as a result.
 *
 * <p>The DataService XML output has the form:
 * <pre>
 *   &lt;WrapperElement&gt;
 *     &lt;RowElement&gt;
 *       &lt;field1&gt;value1&lt;/field1&gt;
 *       &lt;field2&gt;value2&lt;/field2&gt;
 *     &lt;/RowElement&gt;
 *     ...
 *   &lt;/WrapperElement&gt;
 * </pre>
 * This is converted to a Map containing a list keyed by the row element name.
 * </p>
 */
public class GraphQLXmlParser {

    private static final Log log = LogFactory.getLog(GraphQLXmlParser.class);

    private GraphQLXmlParser() {
    }

    /**
     * Converts an XML string (DataService query result) into a Map structure
     * suitable for graphql-java's result handling.
     *
     * <p>The outer wrapper element becomes a Map with one key (the row element name)
     * whose value is a List of Maps, one per row.</p>
     *
     * @param xml the XML string to parse
     * @return a Map representing the result, or an empty Map on empty/null input
     */
    public static Object parseXmlToMap(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Security: disable external entity processing
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            return elementToObject(root);
        } catch (Exception e) {
            log.error("Failed to parse DataService XML result for GraphQL: " + e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("_parseError", e.getMessage());
            return error;
        }
    }

    /**
     * Recursively converts a DOM Element to a Map or List of Maps.
     */
    private static Object elementToObject(Element element) {
        NodeList children = element.getChildNodes();

        // Check if all children are elements (not just text)
        boolean hasElementChildren = false;
        boolean hasTextContent = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
            } else if (child.getNodeType() == Node.TEXT_NODE
                    && !child.getNodeValue().trim().isEmpty()) {
                hasTextContent = true;
            }
        }

        if (!hasElementChildren) {
            // Leaf node: return text content
            return element.getTextContent();
        }

        // Check if children are all the same tag (a list of rows)
        String firstChildTag = null;
        boolean allSameTag = true;
        int elementChildCount = 0;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementChildCount++;
                if (firstChildTag == null) {
                    firstChildTag = child.getNodeName();
                } else if (!firstChildTag.equals(child.getNodeName())) {
                    allSameTag = false;
                }
            }
        }

        if (allSameTag && elementChildCount > 0 && firstChildTag != null) {
            // Looks like a wrapper with repeated row elements
            Map<String, Object> result = new HashMap<>();
            List<Object> rows = new ArrayList<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    rows.add(elementToObject((Element) child));
                }
            }
            result.put(firstChildTag, rows);
            // Also add XML attributes of wrapper
            addAttributes(element, result);
            return result;
        }

        // Mixed children: build a flat Map
        Map<String, Object> map = new HashMap<>();
        addAttributes(element, map);
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String key = childEl.getLocalName() != null ? childEl.getLocalName() : childEl.getNodeName();
                Object existing = map.get(key);
                Object value = elementToObject(childEl);
                if (existing == null) {
                    map.put(key, value);
                } else if (existing instanceof List) {
                    ((List<Object>) existing).add(value);
                } else {
                    List<Object> list = new ArrayList<>();
                    list.add(existing);
                    list.add(value);
                    map.put(key, list);
                }
            }
        }
        return map;
    }

    private static void addAttributes(Element element, Map<String, Object> map) {
        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                map.put(attr.getNodeName(), attr.getNodeValue());
            }
        }
    }
}
