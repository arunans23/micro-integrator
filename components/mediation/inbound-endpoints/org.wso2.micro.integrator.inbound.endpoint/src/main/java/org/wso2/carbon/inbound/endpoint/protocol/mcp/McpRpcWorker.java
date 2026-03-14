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
import org.apache.synapse.transport.passthru.Pipe;
import org.apache.synapse.transport.passthru.SourceContext;
import org.apache.synapse.transport.passthru.SourceRequest;
import org.apache.synapse.transport.passthru.SourceResponse;
import org.apache.synapse.transport.passthru.config.SourceConfiguration;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Worker for {@code POST /mcp} — reads the JSON-RPC 2.0 request body, delegates to
 * {@link McpProtocolHandler}, and writes the JSON response using the PassThrough Pipe mechanism.
 */
public class McpRpcWorker implements Runnable {

    private static final Log log = LogFactory.getLog(McpRpcWorker.class);

    private final SourceRequest request;
    private final SourceConfiguration sourceConfiguration;
    private final McpProtocolHandler protocolHandler;

    public McpRpcWorker(SourceRequest request, SourceConfiguration sourceConfiguration,
                        McpProtocolHandler protocolHandler) {
        this.request = request;
        this.sourceConfiguration = sourceConfiguration;
        this.protocolHandler = protocolHandler;
    }

    @Override
    public void run() {
        try {
            String requestBody = readBody();
            JSONObject responseJson = protocolHandler.handle(requestBody);

            if (responseJson == null) {
                // notifications/initialized — 204 No Content
                sendNoContentResponse();
            } else {
                byte[] responseBytes = responseJson.toString().getBytes(StandardCharsets.UTF_8);
                sendJsonResponse(200, responseBytes);
            }
        } catch (Exception e) {
            log.error("McpRpcWorker failed", e);
            try {
                byte[] errorBytes = ("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":"
                        + "{\"code\":-32603,\"message\":\"Internal server error\"}}")
                        .getBytes(StandardCharsets.UTF_8);
                sendJsonResponse(500, errorBytes);
            } catch (Exception ex) {
                log.error("Failed to send MCP error response", ex);
            }
        }
    }

    private String readBody() throws IOException {
        if (!request.isEntityEnclosing()) {
            return "";
        }
        org.apache.synapse.transport.passthru.Pipe requestPipe = request.getPipe();
        if (requestPipe == null) {
            return "";
        }
        java.io.InputStream in = requestPipe.getInputStream();
        if (in == null) {
            return "";
        }
        byte[] buffer = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private void sendJsonResponse(int statusCode, byte[] body) throws IOException {
        SourceResponse sourceResponse = new SourceResponse(sourceConfiguration, statusCode, request);
        sourceResponse.addHeader(McpConstants.HEADER_CONTENT_TYPE,
                McpConstants.CONTENT_TYPE_JSON + "; charset=UTF-8");

        Pipe pipe = new Pipe(sourceConfiguration.getBufferFactory().getBuffer(),
                "MCP-RPC", sourceConfiguration);
        pipe.attachConsumer(request.getConnection());
        sourceResponse.connect(pipe);

        // Wake the NIO reactor — it will call SourceHandler.responseReady() -> sourceResponse.start()
        SourceContext.setResponse(request.getConnection(), sourceResponse);
        request.getConnection().requestOutput();

        // Write body on this worker thread; the Pipe bridges to the NIO encoder
        try (OutputStream out = pipe.getOutputStream()) {
            out.write(body);
            out.flush();
        }
        pipe.setSerializationComplete(true);
    }

    private void sendNoContentResponse() throws IOException {
        SourceResponse sourceResponse = new SourceResponse(sourceConfiguration, 204, request);
        // 204 has no body — connect null pipe so SourceResponse.write() calls encoder.complete()
        sourceResponse.connect(null);
        SourceContext.setResponse(request.getConnection(), sourceResponse);
        request.getConnection().requestOutput();
    }
}
