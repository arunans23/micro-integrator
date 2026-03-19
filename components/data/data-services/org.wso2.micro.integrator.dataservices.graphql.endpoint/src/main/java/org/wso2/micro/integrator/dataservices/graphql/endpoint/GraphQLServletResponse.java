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

import org.apache.http.protocol.HTTP;

import java.nio.charset.StandardCharsets;

/**
 * Holds the GraphQL JSON response that will be streamed back to the HTTP client.
 */
public class GraphQLServletResponse {

    private static final String APPLICATION_JSON = "application/json";

    private String content;
    private int statusCode = 200;
    private final String contentType = APPLICATION_JSON;
    private final String characterEncoding = StandardCharsets.UTF_8.name();
    private volatile boolean complete = false;
    private volatile boolean streamStarted = false;

    /**
     * Sets the JSON response body and marks the response as ready to stream.
     */
    public void setContent(String jsonContent) {
        this.content = jsonContent;
        this.streamStarted = true;
    }

    /**
     * Marks the response as fully complete.
     */
    public void forceComplete() {
        this.complete = true;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean startStream() {
        return streamStarted;
    }

    public String getContentAsString() {
        return content;
    }

    public int getStatus() {
        return statusCode;
    }

    public void setStatus(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }
}
