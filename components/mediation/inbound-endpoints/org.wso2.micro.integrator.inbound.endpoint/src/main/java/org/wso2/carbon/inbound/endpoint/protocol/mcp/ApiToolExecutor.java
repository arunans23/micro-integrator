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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.api.API;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Executes an MCP tool by dispatching an HTTP request to the corresponding Synapse REST API
 * on localhost. The API context is resolved from the deployed {@link API} artifact.
 */
public class ApiToolExecutor {

    private static final Log log = LogFactory.getLog(ApiToolExecutor.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final SynapseConfiguration synapseConfig;
    private final int mainHttpPort;

    public ApiToolExecutor(SynapseConfiguration synapseConfig, int mainHttpPort) {
        this.synapseConfig = synapseConfig;
        this.mainHttpPort = mainHttpPort;
    }

    /**
     * Executes the API-backed tool and returns the response body as a string.
     *
     * @param tool tool descriptor with API binding details
     * @param args MCP tool arguments
     * @return response body string
     * @throws McpToolExecutionException if the API call fails
     */
    public String execute(McpToolDescriptor tool, JSONObject args) throws McpToolExecutionException {
        String apiContext = resolveApiContext(tool.getApiName());
        String resourcePath = resolveResourcePath(tool.getApiResource(), tool.getPathMappings(), args);
        String queryString = buildQueryString(tool.getQueryMappings(), args);

        String urlStr = "http://localhost:" + mainHttpPort + apiContext + resourcePath
                + (queryString.isEmpty() ? "" : "?" + queryString);

        if (log.isDebugEnabled()) {
            log.debug("MCP tool '" + tool.getName() + "' → " + tool.getApiMethod() + " " + urlStr);
        }

        try {
            return invokeHttp(tool.getApiMethod(), urlStr, args);
        } catch (IOException e) {
            throw new McpToolExecutionException("HTTP call to Synapse API failed for tool '"
                    + tool.getName() + "': " + e.getMessage(), e);
        }
    }

    private String resolveApiContext(String apiName) throws McpToolExecutionException {
        API api = synapseConfig.getAPI(apiName);
        if (api == null) {
            throw new McpToolExecutionException("Synapse API '" + apiName + "' is not deployed");
        }
        String context = api.getContext();
        return context.startsWith("/") ? context : "/" + context;
    }

    private String resolveResourcePath(String resourceTemplate,
                                       List<McpToolDescriptor.ParamMapping> pathMappings,
                                       JSONObject args) {
        if (resourceTemplate == null || resourceTemplate.isEmpty()) {
            return "";
        }
        String path = resourceTemplate;
        for (McpToolDescriptor.ParamMapping mapping : pathMappings) {
            if (args.has(mapping.getArg())) {
                path = path.replace("{" + mapping.getParam() + "}", args.get(mapping.getArg()).toString());
            }
        }
        return path;
    }

    private String buildQueryString(List<McpToolDescriptor.ParamMapping> queryMappings, JSONObject args) {
        StringBuilder sb = new StringBuilder();
        for (McpToolDescriptor.ParamMapping mapping : queryMappings) {
            if (args.has(mapping.getArg())) {
                if (sb.length() > 0) {
                    sb.append("&");
                }
                sb.append(mapping.getParam()).append("=").append(args.get(mapping.getArg()).toString());
            }
        }
        return sb.toString();
    }

    private String invokeHttp(String method, String urlStr, JSONObject args) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Accept", McpConstants.CONTENT_TYPE_JSON);

        // For methods with a body, send remaining args as JSON payload
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            byte[] body = args.toString().getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", McpConstants.CONTENT_TYPE_JSON);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
        }

        int statusCode = conn.getResponseCode();
        boolean isError = statusCode >= 400;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                isError ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString().trim();
        }
    }
}
