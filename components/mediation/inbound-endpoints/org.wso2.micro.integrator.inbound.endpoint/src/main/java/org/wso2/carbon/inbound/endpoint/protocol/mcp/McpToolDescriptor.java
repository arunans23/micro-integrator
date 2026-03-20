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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime descriptor for an MCP tool loaded from a local entry.
 */
public class McpToolDescriptor {

    public enum BindingType { API, SEQUENCE, MANAGEMENT_API }

    // Core tool metadata
    private String name;
    private String description;
    private List<InputProperty> inputProperties = new ArrayList<>();

    // Binding
    private BindingType bindingType;

    // API binding fields
    private String apiName;
    private String apiResource;
    private String apiMethod;
    private List<ParamMapping> pathMappings = new ArrayList<>();
    private List<ParamMapping> queryMappings = new ArrayList<>();

    // Management API binding fields (reuses apiMethod, pathMappings, queryMappings)
    private String managementPath;

    // Sequence binding fields
    private String sequenceName;

    // --- Getters / Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<InputProperty> getInputProperties() { return inputProperties; }
    public void addInputProperty(InputProperty p) { inputProperties.add(p); }

    public BindingType getBindingType() { return bindingType; }
    public void setBindingType(BindingType bindingType) { this.bindingType = bindingType; }

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public String getApiResource() { return apiResource; }
    public void setApiResource(String apiResource) { this.apiResource = apiResource; }

    public String getApiMethod() { return apiMethod; }
    public void setApiMethod(String apiMethod) { this.apiMethod = apiMethod; }

    public List<ParamMapping> getPathMappings() { return pathMappings; }
    public void addPathMapping(ParamMapping m) { pathMappings.add(m); }

    public List<ParamMapping> getQueryMappings() { return queryMappings; }
    public void addQueryMapping(ParamMapping m) { queryMappings.add(m); }

    public String getManagementPath() { return managementPath; }
    public void setManagementPath(String managementPath) { this.managementPath = managementPath; }

    public String getSequenceName() { return sequenceName; }
    public void setSequenceName(String sequenceName) { this.sequenceName = sequenceName; }

    /**
     * Serializes this tool to the MCP tools/list JSON format.
     */
    public JSONObject toMcpJson() {
        JSONObject tool = new JSONObject();
        tool.put("name", name);
        tool.put("description", description != null ? description : "");

        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();

        for (InputProperty p : inputProperties) {
            JSONObject prop = new JSONObject();
            prop.put("type", p.getType());
            if (p.getDescription() != null) {
                prop.put("description", p.getDescription());
            }
            properties.put(p.getName(), prop);
            if (p.isRequired()) {
                required.put(p.getName());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        tool.put("inputSchema", schema);
        return tool;
    }

    // --- Inner classes ---

    public static class InputProperty {
        private String name;
        private String type;
        private boolean required;
        private String description;

        public InputProperty(String name, String type, boolean required, String description) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.description = description;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isRequired() { return required; }
        public String getDescription() { return description; }
    }

    public static class ParamMapping {
        private final String param;  // API param name (path variable or query param)
        private final String arg;    // MCP argument name

        public ParamMapping(String param, String arg) {
            this.param = param;
            this.arg = arg;
        }

        public String getParam() { return param; }
        public String getArg() { return arg; }
    }
}
