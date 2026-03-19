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

import org.apache.axis2.Constants;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.AbstractSynapseHandler;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.util.RelayUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.ws.rs.core.MediaType;
import javax.xml.stream.XMLStreamException;

/**
 * Synapse handler that intercepts HTTP requests to {@code /graphql/{serviceName}},
 * executes the GraphQL query, and sends the JSON response back to the client.
 *
 * <p>Registered via the Synapse handler chain in the Axis2 configuration.</p>
 */
public class GraphQLPassThroughHandler extends AbstractSynapseHandler {

    private static final Log LOG = LogFactory.getLog(GraphQLPassThroughHandler.class);

    private static final String IS_GRAPHQL_SERVICE = "IsGraphQLService";
    private static final String TRANSPORT_IN_URL = "TransportInURL";
    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_INTERNAL_SERVER_ERROR = 500;

    @Override
    public boolean handleRequestInFlow(org.apache.synapse.MessageContext messageContext) {
        try {
            MessageContext axis2MessageContext =
                    ((Axis2MessageContext) messageContext).getAxis2MessageContext();

            Object isGraphQLService = axis2MessageContext.getProperty(IS_GRAPHQL_SERVICE);
            String transportInUrl = (String) axis2MessageContext.getProperty(TRANSPORT_IN_URL);

            if (transportInUrl == null || isGraphQLService == null) {
                return true;
            }

            // Skip Synapse main sequence — we handle the response here
            messageContext.setProperty(SynapseConstants.SKIP_MAIN_SEQUENCE, Boolean.TRUE);

            // Build the incoming message so we can read the body
            RelayUtils.buildMessage(axis2MessageContext);

            // Extract JSON request body
            String requestBody = extractRequestBody(axis2MessageContext);

            // Execute the GraphQL request
            String jsonResponse = GraphQLEndpoint.process(requestBody, transportInUrl);

            // Write the response back to the client
            synchronized (this) {
                GraphQLServletResponse servletResponse = new GraphQLServletResponse();
                servletResponse.setContent(jsonResponse);
                servletResponse.setStatus(HTTP_OK);
                servletResponse.forceComplete();
                sendResponseBack(servletResponse, messageContext, axis2MessageContext);
            }

        } catch (Exception e) {
            LOG.error("Error processing GraphQL request.", e);
            this.handleException("Error occurred in GraphQL handler.", e, messageContext);
        }
        return true;
    }

    /**
     * Extracts the JSON request body from the Axis2 MessageContext.
     */
    private String extractRequestBody(MessageContext axis2MessageContext) {
        try {
            // Try to get JSON payload using the Synapse JSON utility
            org.apache.synapse.commons.json.JsonUtil.jsonPayloadToString(axis2MessageContext);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            org.apache.synapse.commons.json.JsonUtil.writeAsJson(axis2MessageContext, baos);
            return baos.toString("UTF-8");
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not read JSON body using JsonUtil; falling back to envelope text: "
                        + e.getMessage());
            }
            // Fallback: get from SOAP body
            try {
                org.apache.axiom.om.OMElement body =
                        axis2MessageContext.getEnvelope().getBody().getFirstElement();
                if (body != null) {
                    return body.getText();
                }
            } catch (Exception ex) {
                LOG.warn("Could not extract request body for GraphQL: " + ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Sends the GraphQL response back to the HTTP client via the PassThrough transport.
     */
    private void sendResponseBack(GraphQLServletResponse servletResponse,
                                  org.apache.synapse.MessageContext messageContext,
                                  MessageContext axis2MessageContext) throws IOException {
        axis2MessageContext.setProperty(Constants.Configuration.MESSAGE_TYPE, MediaType.APPLICATION_JSON);
        axis2MessageContext.removeProperty(PassThroughConstants.NO_ENTITY_BODY);
        messageContext.setTo(null);
        messageContext.setResponse(true);

        GraphQLAxisEngine engine = new GraphQLAxisEngine();
        try {
            engine.send(axis2MessageContext, servletResponse);
        } catch (Exception e) {
            throw new IOException("Error sending GraphQL response: " + e.getMessage(), e);
        }
    }

    private void handleException(String msg, Exception e,
                                 org.apache.synapse.MessageContext msgContext) {
        LOG.error(msg, e);
        if (msgContext.getServiceLog() != null) {
            msgContext.getServiceLog().error(msg, e);
        }
        throw new SynapseException(msg, e);
    }

    @Override
    public boolean handleRequestOutFlow(org.apache.synapse.MessageContext messageContext) {
        return true;
    }

    @Override
    public boolean handleResponseInFlow(org.apache.synapse.MessageContext messageContext) {
        return true;
    }

    @Override
    public boolean handleResponseOutFlow(org.apache.synapse.MessageContext messageContext) {
        return true;
    }
}
