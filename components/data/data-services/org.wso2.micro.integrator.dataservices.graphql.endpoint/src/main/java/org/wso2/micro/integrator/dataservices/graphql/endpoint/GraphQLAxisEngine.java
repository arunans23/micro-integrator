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

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.util.LoggingControl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Sends a GraphQL response back to the HTTP client via the PassThrough transport.
 * Adapted from {@code ODataAxisEngine}.
 */
public class GraphQLAxisEngine extends AxisEngine {

    private static final Log LOG = LogFactory.getLog(GraphQLAxisEngine.class);
    private static GraphQLTransportSender sender;

    /**
     * Sends the GraphQL response back to the client.
     *
     * @param messageContext the Axis2 message context
     * @param response       the response container holding the JSON body
     * @throws AxisFault if any error occurs while sending the message
     */
    public void send(MessageContext messageContext, GraphQLServletResponse response) throws AxisFault {
        if (LoggingControl.debugLoggingAllowed && LOG.isTraceEnabled()) {
            LOG.trace(messageContext.getLogIDString() + " graphql-send:" + messageContext.getMessageID());
        }
        if (sender == null) {
            sender = new GraphQLTransportSender(
                    messageContext.getConfigurationContext(),
                    messageContext.getTransportOut(),
                    response);
        } else {
            sender.setResponse(response);
        }
        sender.invoke(messageContext);
        flowComplete(messageContext);
    }
}
