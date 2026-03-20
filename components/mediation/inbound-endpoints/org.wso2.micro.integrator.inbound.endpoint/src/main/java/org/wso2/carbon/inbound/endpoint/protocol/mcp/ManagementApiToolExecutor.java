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
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Executes an MCP tool by calling a WSO2 MI Management API resource on localhost.
 *
 * <p>Targets the internal HTTP port (default 9201) and authenticates with Basic auth
 * using the credentials configured in the MCP inbound endpoint parameters
 * ({@code mcp.management.user} / {@code mcp.management.password}).
 *
 * <p>Tool definition example:
 * <pre>{@code
 * <managementApiBinding>
 *     <path>/management/apis</path>
 *     <method>GET</method>
 *     <parameterMapping>
 *         <query param="apiName" arg="name"/>
 *     </parameterMapping>
 * </managementApiBinding>
 * }</pre>
 */
public class ManagementApiToolExecutor {

    private static final Log log = LogFactory.getLog(ManagementApiToolExecutor.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final int managementApiPort;
    private final String authHeader;

    public ManagementApiToolExecutor(int managementApiPort, String username, String password) {
        this.managementApiPort = managementApiPort;
        String credentials = username + ":" + password;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Executes the management API-backed tool and returns the response body.
     *
     * @param tool tool descriptor with management API binding details
     * @param args MCP tool arguments
     * @return response body string (JSON from the Management API)
     * @throws McpToolExecutionException if the HTTP call fails
     */
    public String execute(McpToolDescriptor tool, JSONObject args) throws McpToolExecutionException {
        String resolvedPath = resolvePathParams(tool.getManagementPath(), tool.getPathMappings(), args);
        String queryString = buildQueryString(tool.getQueryMappings(), args);

        String urlStr = "http://localhost:" + managementApiPort + resolvedPath
                + (queryString.isEmpty() ? "" : "?" + queryString);

        log.info("MCP management tool '" + tool.getName() + "' → "
                + tool.getApiMethod() + " " + urlStr);

        try {
            return invokeHttp(tool.getApiMethod(), urlStr, args);
        } catch (IOException e) {
            throw new McpToolExecutionException(
                    "HTTP call to Management API failed for tool '" + tool.getName()
                            + "': " + e.getMessage(), e);
        }
    }

    private String resolvePathParams(String pathTemplate,
                                     List<McpToolDescriptor.ParamMapping> pathMappings,
                                     JSONObject args) {
        String path = pathTemplate;
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
        conn.setRequestProperty("Authorization", authHeader);

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

        java.io.InputStream responseStream = isError ? conn.getErrorStream() : conn.getInputStream();
        if (responseStream == null) {
            // getErrorStream() returns null when the server sends no error body (e.g. 401 with empty body)
            throw new IOException("HTTP " + statusCode + " from Management API with no response body: " + urlStr);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            String body = response.toString().trim();
            log.info("Management API " + method + " " + urlStr
                    + " → status=" + statusCode + " body_length=" + body.length());
            return body;
        }
    }
}
