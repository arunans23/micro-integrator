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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides pre-built {@link McpToolDescriptor}s for all WSO2 MI Management API resources.
 *
 * <p>Used by {@link McpManagementServer} when MCP support is enabled via the
 * {@code mi.management.mcp.enabled} system property. No local entries or inbound endpoint
 * configuration is required.
 */
public class McpManagementToolRegistry {

    private static final List<McpToolDescriptor> TOOLS;

    static {
        List<McpToolDescriptor> tools = new ArrayList<>();

        // ---- APIs -----------------------------------------------------------
        tools.add(listTool("list_apis",
                "List all REST APIs deployed in WSO2 Micro Integrator",
                "/management/apis"));
        tools.add(getTool("get_api",
                "Get details of a specific REST API deployed in WSO2 Micro Integrator",
                "/management/apis", "apiName", "name",
                "Name of the REST API"));

        // ---- Proxy services -------------------------------------------------
        tools.add(listTool("list_proxy_services",
                "List all proxy services deployed in WSO2 Micro Integrator",
                "/management/proxy-services"));
        tools.add(getTool("get_proxy_service",
                "Get details of a specific proxy service",
                "/management/proxy-services", "proxyServiceName", "name",
                "Name of the proxy service"));

        // ---- Sequences ------------------------------------------------------
        tools.add(listTool("list_sequences",
                "List all mediation sequences deployed in WSO2 Micro Integrator",
                "/management/sequences"));
        tools.add(getTool("get_sequence",
                "Get details of a specific mediation sequence",
                "/management/sequences", "sequenceName", "name",
                "Name of the sequence"));

        // ---- Endpoints ------------------------------------------------------
        tools.add(listTool("list_endpoints",
                "List all endpoints deployed in WSO2 Micro Integrator",
                "/management/endpoints"));
        tools.add(getTool("get_endpoint",
                "Get details of a specific endpoint",
                "/management/endpoints", "endpointName", "name",
                "Name of the endpoint"));

        // ---- Inbound endpoints ----------------------------------------------
        tools.add(listTool("list_inbound_endpoints",
                "List all inbound endpoints deployed in WSO2 Micro Integrator",
                "/management/inbound-endpoints"));
        tools.add(getTool("get_inbound_endpoint",
                "Get details of a specific inbound endpoint",
                "/management/inbound-endpoints", "inboundEndpointName", "name",
                "Name of the inbound endpoint"));

        // ---- Message stores -------------------------------------------------
        tools.add(listTool("list_message_stores",
                "List all message stores deployed in WSO2 Micro Integrator",
                "/management/message-stores"));
        tools.add(getTool("get_message_store",
                "Get details of a specific message store",
                "/management/message-stores", "name", "name",
                "Name of the message store"));

        // ---- Message processors ---------------------------------------------
        tools.add(listTool("list_message_processors",
                "List all message processors deployed in WSO2 Micro Integrator",
                "/management/message-processors"));
        tools.add(getTool("get_message_processor",
                "Get details of a specific message processor",
                "/management/message-processors", "name", "name",
                "Name of the message processor"));

        // ---- Local entries --------------------------------------------------
        tools.add(listTool("list_local_entries",
                "List all local entries deployed in WSO2 Micro Integrator",
                "/management/local-entries"));
        tools.add(getTool("get_local_entry",
                "Get details of a specific local entry",
                "/management/local-entries", "name", "name",
                "Name of the local entry"));

        // ---- Tasks ----------------------------------------------------------
        tools.add(listTool("list_tasks",
                "List all scheduled tasks deployed in WSO2 Micro Integrator",
                "/management/tasks"));
        tools.add(getTool("get_task",
                "Get details of a specific scheduled task",
                "/management/tasks", "taskName", "name",
                "Name of the scheduled task"));

        // ---- Templates ------------------------------------------------------
        tools.add(listTool("list_templates",
                "List all templates deployed in WSO2 Micro Integrator",
                "/management/templates"));
        tools.add(getTool("get_template",
                "Get details of a specific template",
                "/management/templates", "templateName", "name",
                "Name of the template"));

        // ---- Connectors -----------------------------------------------------
        tools.add(listTool("list_connectors",
                "List all connectors deployed in WSO2 Micro Integrator",
                "/management/connectors"));

        // ---- Data services --------------------------------------------------
        tools.add(listTool("list_data_services",
                "List all data services deployed in WSO2 Micro Integrator",
                "/management/data-services"));
        tools.add(getTool("get_data_service",
                "Get details of a specific data service",
                "/management/data-services", "dataServiceName", "name",
                "Name of the data service"));

        // ---- Server info ----------------------------------------------------
        tools.add(listTool("get_server_info",
                "Get WSO2 Micro Integrator server information (version, startup time, Java version, etc.)",
                "/management/server"));

        // ---- Logging --------------------------------------------------------
        tools.add(listTool("get_log_levels",
                "Get the current log levels of all loggers in WSO2 Micro Integrator",
                "/management/logging"));

        TOOLS = Collections.unmodifiableList(tools);
    }

    private McpManagementToolRegistry() {
    }

    /**
     * Returns the immutable list of MCP tool descriptors for all management API resources.
     */
    public static List<McpToolDescriptor> getTools() {
        return TOOLS;
    }

    // ---- builder helpers ----------------------------------------------------

    /** Builds a list tool (no parameters, GET request). */
    private static McpToolDescriptor listTool(String name, String description, String path) {
        McpToolDescriptor tool = new McpToolDescriptor();
        tool.setName(name);
        tool.setDescription(description);
        tool.setBindingType(McpToolDescriptor.BindingType.MANAGEMENT_API);
        tool.setManagementPath(path);
        tool.setApiMethod("GET");
        return tool;
    }

    /**
     * Builds a get-by-name tool with a single required query parameter.
     *
     * @param name        MCP tool name
     * @param description tool description
     * @param path        management API path
     * @param queryParam  the query parameter name accepted by the management API
     * @param argName     the MCP argument name the AI uses
     * @param argDesc     description of the argument
     */
    private static McpToolDescriptor getTool(String name, String description, String path,
                                             String queryParam, String argName, String argDesc) {
        McpToolDescriptor tool = new McpToolDescriptor();
        tool.setName(name);
        tool.setDescription(description);
        tool.setBindingType(McpToolDescriptor.BindingType.MANAGEMENT_API);
        tool.setManagementPath(path);
        tool.setApiMethod("GET");
        tool.addInputProperty(new McpToolDescriptor.InputProperty(argName, "string", true, argDesc));
        tool.addQueryMapping(new McpToolDescriptor.ParamMapping(queryParam, argName));
        return tool;
    }
}
