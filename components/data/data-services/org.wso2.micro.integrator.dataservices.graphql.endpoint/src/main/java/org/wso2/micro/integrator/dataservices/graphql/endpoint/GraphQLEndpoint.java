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
package org.wso2.micro.integrator.dataservices.graphql.endpoint;

import com.google.gson.Gson;
import graphql.ExecutionResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.wso2.micro.integrator.dataservices.core.graphql.GraphQLServiceHandler;
import org.wso2.micro.integrator.dataservices.core.graphql.GraphQLServiceRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry point for GraphQL data service requests.
 * Resolves the target service from the URL, delegates execution to
 * {@link GraphQLServiceHandler}, and serialises the result as JSON.
 *
 * <p>Expected URL format: {@code /graphql/{serviceName}}</p>
 */
public class GraphQLEndpoint {

    private static final Log log = LogFactory.getLog(GraphQLEndpoint.class);
    private static final String GRAPHQL_SERVICE_PREFIX = "graphql/";
    private static final Gson GSON = new Gson();

    private GraphQLEndpoint() {
    }

    /**
     * Processes a GraphQL request.
     *
     * @param requestBody the raw JSON request body containing {@code query},
     *                    optional {@code operationName} and optional {@code variables}
     * @param requestUri  the full request URI used to extract the service name
     * @return a JSON string containing the GraphQL {@code data} and/or {@code errors}
     */
    public static String process(String requestBody, String requestUri) {
        try {
            String serviceName = extractServiceName(requestUri);
            if (serviceName == null) {
                return errorResponse("Could not determine service name from URI: " + requestUri);
            }

            GraphQLServiceRegistry registry = GraphQLServiceRegistry.getInstance();
            GraphQLServiceHandler handler = registry.getServiceHandler(serviceName);
            if (handler == null) {
                return errorResponse("No GraphQL service found for: " + serviceName);
            }

            // Parse request body
            String query = null;
            String operationName = null;
            Map<String, Object> variables = Collections.emptyMap();

            if (requestBody != null && !requestBody.trim().isEmpty()) {
                try {
                    JSONObject requestJson = new JSONObject(requestBody);
                    query = requestJson.optString("query", null);
                    operationName = requestJson.optString("operationName", null);
                    if (operationName != null && operationName.isEmpty()) {
                        operationName = null;
                    }
                    JSONObject vars = requestJson.optJSONObject("variables");
                    if (vars != null) {
                        variables = jsonObjectToMap(vars);
                    }
                } catch (Exception e) {
                    return errorResponse("Invalid JSON request body: " + e.getMessage());
                }
            }

            if (query == null || query.trim().isEmpty()) {
                return errorResponse("GraphQL query must not be empty");
            }

            if (log.isDebugEnabled()) {
                log.debug("Executing GraphQL query for service '" + serviceName + "': " + query);
            }

            ExecutionResult result = handler.execute(query, operationName, variables);
            Map<String, Object> spec = result.toSpecification();
            return GSON.toJson(spec);

        } catch (Exception e) {
            log.error("Unexpected error processing GraphQL request for URI: " + requestUri, e);
            return errorResponse("Internal server error: " + e.getMessage());
        }
    }

    /**
     * Extracts the data service name from the request URI.
     * e.g. {@code /graphql/MyService} → {@code MyService}
     */
    static String extractServiceName(String uri) {
        if (uri == null) return null;
        int idx = uri.indexOf(GRAPHQL_SERVICE_PREFIX);
        if (idx == -1) return null;
        String afterPrefix = uri.substring(idx + GRAPHQL_SERVICE_PREFIX.length());
        // Strip trailing slashes and query parameters
        int slash = afterPrefix.indexOf('/');
        int question = afterPrefix.indexOf('?');
        int end = afterPrefix.length();
        if (slash >= 0 && slash < end) end = slash;
        if (question >= 0 && question < end) end = question;
        String serviceName = afterPrefix.substring(0, end).trim();
        return serviceName.isEmpty() ? null : serviceName;
    }

    private static String errorResponse(String message) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("message", message);
        Map<String, Object> response = new HashMap<>();
        response.put("errors", Collections.singletonList(errorMap));
        return GSON.toJson(response);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> jsonObjectToMap(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                map.put(key, jsonObjectToMap((JSONObject) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }
}
