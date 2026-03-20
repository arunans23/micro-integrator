/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.inbound.endpoint.protocol.mcp;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.config.SynapseConfiguration;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Loads MCP tool descriptors from Synapse local entries.
 *
 * Each tool is defined as a local entry whose value is an {@code <mcpTool>} XML element.
 * Tool definitions are re-read on every call so hot-deployed local entry updates take
 * effect immediately without a server restart.
 *
 * <p>All element lookups use local name matching (namespace-agnostic) because child elements
 * inherit the {@code xmlns="http://ws.apache.org/ns/synapse"} default namespace from the
 * enclosing {@code <localEntry>}, making QName-based lookups with no namespace fail.
 */
public class McpToolDefinitionLoader {

    private static final Log log = LogFactory.getLog(McpToolDefinitionLoader.class);

    private McpToolDefinitionLoader() {
    }

    /**
     * Loads tool descriptors from the given comma-separated list of local entry keys.
     *
     * @param toolKeys comma-separated local entry keys
     * @param config   the current Synapse configuration
     * @return list of parsed tool descriptors; entries that fail to parse are skipped
     */
    public static List<McpToolDescriptor> load(String toolKeys, SynapseConfiguration config) {
        List<McpToolDescriptor> tools = new ArrayList<>();
        if (toolKeys == null || toolKeys.trim().isEmpty()) {
            return tools;
        }
        for (String key : toolKeys.split(",")) {
            key = key.trim();
            if (key.isEmpty()) {
                continue;
            }
            Object entry = config.getEntry(key);
            if (!(entry instanceof OMElement)) {
                log.warn("MCP tool local entry '" + key + "' is missing or is not an XML element — skipping");
                continue;
            }
            OMElement root = (OMElement) entry;
            if (!McpConstants.ELEM_MCP_TOOL.equals(root.getLocalName())) {
                log.warn("Local entry '" + key + "' root element is <" + root.getLocalName()
                        + ">, expected <mcpTool> — skipping");
                continue;
            }
            try {
                tools.add(parseTool(root, key));
            } catch (Exception e) {
                log.error("Failed to parse MCP tool definition from local entry '" + key + "'", e);
            }
        }
        return tools;
    }

    private static McpToolDescriptor parseTool(OMElement root, String localEntryKey) {
        McpToolDescriptor tool = new McpToolDescriptor();

        OMElement nameElem = firstChild(root, McpConstants.ELEM_NAME);
        if (nameElem == null || nameElem.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing or empty <name> in MCP tool local entry '" + localEntryKey + "'");
        }
        tool.setName(nameElem.getText().trim());

        OMElement descElem = firstChild(root, McpConstants.ELEM_DESCRIPTION);
        if (descElem != null) {
            tool.setDescription(descElem.getText().trim());
        }

        OMElement schemaElem = firstChild(root, McpConstants.ELEM_INPUT_SCHEMA);
        if (schemaElem != null) {
            parseInputSchema(schemaElem, tool);
        }

        OMElement apiBinding = firstChild(root, McpConstants.ELEM_API_BINDING);
        OMElement seqBinding = firstChild(root, McpConstants.ELEM_SEQUENCE_BINDING);
        OMElement mgmtBinding = firstChild(root, McpConstants.ELEM_MANAGEMENT_API_BINDING);

        if (apiBinding != null) {
            parseApiBinding(apiBinding, tool, localEntryKey);
        } else if (seqBinding != null) {
            parseSequenceBinding(seqBinding, tool, localEntryKey);
        } else if (mgmtBinding != null) {
            parseManagementApiBinding(mgmtBinding, tool, localEntryKey);
        } else {
            throw new IllegalArgumentException(
                    "MCP tool '" + tool.getName() + "' in local entry '" + localEntryKey
                    + "' has no <apiBinding>, <sequenceBinding>, or <managementApiBinding>");
        }
        return tool;
    }

    private static void parseInputSchema(OMElement schemaElem, McpToolDescriptor tool) {
        Iterator<OMElement> props = schemaElem.getChildrenWithLocalName(McpConstants.ELEM_PROPERTY);
        while (props.hasNext()) {
            OMElement prop = props.next();
            // Attributes have no namespace — plain QName lookup is correct here
            String name = prop.getAttributeValue(new QName(McpConstants.ATTR_NAME));
            String type = prop.getAttributeValue(new QName(McpConstants.ATTR_TYPE));
            String requiredStr = prop.getAttributeValue(new QName(McpConstants.ATTR_REQUIRED));
            String desc = prop.getAttributeValue(new QName(McpConstants.ATTR_DESCRIPTION));

            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            tool.addInputProperty(new McpToolDescriptor.InputProperty(
                    name.trim(),
                    type != null ? type.trim() : "string",
                    "true".equalsIgnoreCase(requiredStr),
                    desc
            ));
        }
    }

    private static void parseApiBinding(OMElement apiBinding, McpToolDescriptor tool, String key) {
        tool.setBindingType(McpToolDescriptor.BindingType.API);

        OMElement apiElem = firstChild(apiBinding, McpConstants.ELEM_API);
        if (apiElem == null || apiElem.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing <api> in <apiBinding> for tool in local entry '" + key + "'");
        }
        tool.setApiName(apiElem.getText().trim());

        OMElement resourceElem = firstChild(apiBinding, McpConstants.ELEM_RESOURCE);
        if (resourceElem != null) {
            tool.setApiResource(resourceElem.getText().trim());
        }

        OMElement methodElem = firstChild(apiBinding, McpConstants.ELEM_METHOD);
        tool.setApiMethod(methodElem != null ? methodElem.getText().trim().toUpperCase() : "GET");

        OMElement mappingElem = firstChild(apiBinding, McpConstants.ELEM_PARAMETER_MAPPING);
        if (mappingElem != null) {
            Iterator<OMElement> mappings = mappingElem.getChildElements();
            while (mappings.hasNext()) {
                OMElement mapping = mappings.next();
                String param = mapping.getAttributeValue(new QName(McpConstants.ATTR_PARAM));
                String arg = mapping.getAttributeValue(new QName(McpConstants.ATTR_ARG));
                if (param == null || arg == null) {
                    continue;
                }
                if (McpConstants.ELEM_PATH.equals(mapping.getLocalName())) {
                    tool.addPathMapping(new McpToolDescriptor.ParamMapping(param, arg));
                } else if (McpConstants.ELEM_QUERY.equals(mapping.getLocalName())) {
                    tool.addQueryMapping(new McpToolDescriptor.ParamMapping(param, arg));
                }
            }
        }
    }

    private static void parseManagementApiBinding(OMElement mgmtBinding, McpToolDescriptor tool, String key) {
        tool.setBindingType(McpToolDescriptor.BindingType.MANAGEMENT_API);

        OMElement pathElem = firstChild(mgmtBinding, McpConstants.ELEM_PATH);
        if (pathElem == null || pathElem.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing <path> in <managementApiBinding> for tool in local entry '" + key + "'");
        }
        tool.setManagementPath(pathElem.getText().trim());

        OMElement methodElem = firstChild(mgmtBinding, McpConstants.ELEM_METHOD);
        tool.setApiMethod(methodElem != null ? methodElem.getText().trim().toUpperCase() : "GET");

        OMElement mappingElem = firstChild(mgmtBinding, McpConstants.ELEM_PARAMETER_MAPPING);
        if (mappingElem != null) {
            Iterator<OMElement> mappings = mappingElem.getChildElements();
            while (mappings.hasNext()) {
                OMElement mapping = mappings.next();
                String param = mapping.getAttributeValue(new QName(McpConstants.ATTR_PARAM));
                String arg = mapping.getAttributeValue(new QName(McpConstants.ATTR_ARG));
                if (param == null || arg == null) {
                    continue;
                }
                if (McpConstants.ELEM_PATH.equals(mapping.getLocalName())) {
                    tool.addPathMapping(new McpToolDescriptor.ParamMapping(param, arg));
                } else if (McpConstants.ELEM_QUERY.equals(mapping.getLocalName())) {
                    tool.addQueryMapping(new McpToolDescriptor.ParamMapping(param, arg));
                }
            }
        }
    }

    private static void parseSequenceBinding(OMElement seqBinding, McpToolDescriptor tool, String key) {
        tool.setBindingType(McpToolDescriptor.BindingType.SEQUENCE);

        OMElement seqElem = firstChild(seqBinding, McpConstants.ELEM_SEQUENCE);
        if (seqElem == null || seqElem.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing <sequence> in <sequenceBinding> for tool in local entry '" + key + "'");
        }
        tool.setSequenceName(seqElem.getText().trim());
    }

    /**
     * Returns the first child element matching the given local name, ignoring namespace.
     * This is necessary because child elements inherit the Synapse default namespace
     * ({@code http://ws.apache.org/ns/synapse}) from the enclosing {@code <localEntry>},
     * so QName-based lookups with no namespace always return null.
     */
    private static OMElement firstChild(OMElement parent, String localName) {
        Iterator<OMElement> it = parent.getChildrenWithLocalName(localName);
        return it.hasNext() ? it.next() : null;
    }
}
