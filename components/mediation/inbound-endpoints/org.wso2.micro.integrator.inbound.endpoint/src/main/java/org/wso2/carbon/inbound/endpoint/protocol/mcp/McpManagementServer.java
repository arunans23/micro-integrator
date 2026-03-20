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
import org.apache.synapse.transport.passthru.api.PassThroughInboundEndpointHandler;
import org.apache.synapse.transport.passthru.config.SourceConfiguration;
import org.wso2.carbon.inbound.endpoint.protocol.http.management.HTTPEndpointManager;

import java.net.InetSocketAddress;

/**
 * Auto-starts an MCP server that exposes all WSO2 MI Management API resources as MCP tools.
 *
 * <p>Activation is controlled entirely by system properties — no inbound endpoint XML or
 * local entry configuration is required:
 *
 * <pre>
 * -Dmi.management.mcp.enabled=true          # required to activate
 * -Dmi.management.mcp.port=7444             # MCP server port (default: 7444)
 * -Dmi.management.mcp.server.name=...       # server name advertised to MCP clients
 * -Dmi.management.mcp.server.version=...    # server version advertised to MCP clients
 * -Dmi.management.mcp.user=admin            # management API username (default: admin)
 * -Dmi.management.mcp.password=admin        # management API password (default: admin)
 * </pre>
 *
 * <p>The server is started by {@link org.wso2.carbon.inbound.endpoint.EndpointListenerLoader}
 * after all standard listeners are up.
 */
public class McpManagementServer {

    private static final Log log = LogFactory.getLog(McpManagementServer.class);

    public static final String PROP_ENABLED = "mi.management.mcp.enabled";
    public static final String PROP_PORT = "mi.management.mcp.port";
    public static final String PROP_SERVER_NAME = "mi.management.mcp.server.name";
    public static final String PROP_SERVER_VERSION = "mi.management.mcp.server.version";
    public static final String PROP_USER = "mi.management.mcp.user";
    public static final String PROP_PASSWORD = "mi.management.mcp.password";

    private static final int DEFAULT_PORT = 7444;
    private static final String DEFAULT_SERVER_NAME = "WSO2 Micro Integrator Management";
    private static final String DEFAULT_SERVER_VERSION = McpConstants.DEFAULT_SERVER_VERSION;
    private static final String ENDPOINT_NAME = "McpManagementServer";

    private McpManagementServer() {
    }

    /**
     * Starts the management MCP server if {@code mi.management.mcp.enabled=true}.
     * Safe to call unconditionally at startup — returns immediately if disabled.
     */
    public static void startIfEnabled() {
        if (!Boolean.getBoolean(PROP_ENABLED)) {
            return;
        }

        int port = Integer.getInteger(PROP_PORT, DEFAULT_PORT);
        String serverName = System.getProperty(PROP_SERVER_NAME, DEFAULT_SERVER_NAME);
        String serverVersion = System.getProperty(PROP_SERVER_VERSION, DEFAULT_SERVER_VERSION);
        String user = System.getProperty(PROP_USER, McpConstants.DEFAULT_MANAGEMENT_USER);
        String password = System.getProperty(PROP_PASSWORD, McpConstants.DEFAULT_MANAGEMENT_PASSWORD);

        int managementApiPort = HTTPEndpointManager.getInstance().getInternalInboundHttpPort();

        log.info("MCP management server enabled — starting on port " + port
                + " (management API port: " + managementApiPort + ")");

        SourceConfiguration sourceConfig;
        try {
            sourceConfig = PassThroughInboundEndpointHandler.getPassThroughSourceConfiguration();
        } catch (Exception e) {
            log.error("MCP management server could not obtain PassThrough source configuration"
                    + " — ensure the HTTP transport is started before calling startIfEnabled()", e);
            return;
        }
        if (sourceConfig == null) {
            log.error("MCP management server: PassThrough source configuration is null — skipping start");
            return;
        }

        McpProtocolHandler protocolHandler = new McpProtocolHandler(
                serverName, serverVersion,
                McpManagementToolRegistry::getTools,
                managementApiPort, user, password);

        McpSourceHandler sourceHandler = new McpSourceHandler(sourceConfig, protocolHandler);

        try {
            PassThroughInboundEndpointHandler.startEndpoint(
                    new InetSocketAddress(port), sourceHandler, ENDPOINT_NAME);
            log.info("MCP management server started on port " + port
                    + " — " + McpManagementToolRegistry.getTools().size() + " tools exposed");
        } catch (Exception e) {
            log.error("Failed to start MCP management server on port " + port, e);
        }
    }
}
