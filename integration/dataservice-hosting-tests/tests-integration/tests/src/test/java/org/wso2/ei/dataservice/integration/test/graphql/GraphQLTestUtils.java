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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.ei.dataservice.integration.test.graphql;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HTTP utility methods for GraphQL integration tests.
 *
 * <p>GraphQL requests are always HTTP POST with a JSON body of the form:
 * <pre>{"query":"...", "operationName":"...", "variables":{...}}</pre>
 * Responses are JSON objects with a "data" key and an optional "errors" key.
 */
public class GraphQLTestUtils {

    public static final int OK = 200;

    private GraphQLTestUtils() {
    }

    /**
     * Sends a GraphQL request (query or mutation) to the given endpoint.
     *
     * @param endpoint the GraphQL service URL, e.g. {@code http://localhost:8480/graphql/GraphQLSampleService}
     * @param body     the raw JSON body, e.g. {@code {"query":"{ getCustomers { Customer { customerName } } }"}}
     * @return an Object array where index 0 is the HTTP status code (Integer)
     *         and index 1 is the response body string
     */
    public static Object[] sendRequest(String endpoint, String body) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(endpoint);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Accept", "application/json");
            HttpEntity entity = new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8));
            post.setEntity(entity);

            try (CloseableHttpResponse response = client.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                if (response.getEntity() != null) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    return new Object[]{ status, sb.toString() };
                }
                return new Object[]{ status, "" };
            }
        }
    }

    /**
     * Convenience wrapper: builds a minimal GraphQL JSON body for a query/mutation string
     * and POSTs it.
     *
     * @param endpoint      the GraphQL service URL
     * @param graphqlQuery  the GraphQL query/mutation document, e.g. {@code { getCustomers { ... } }}
     * @return Object[] {statusCode, responseBody}
     */
    public static Object[] execute(String endpoint, String graphqlQuery) throws IOException {
        // Escape the query string for embedding in JSON
        String escaped = graphqlQuery
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        String body = "{\"query\":\"" + escaped + "\"}";
        return sendRequest(endpoint, body);
    }
}
