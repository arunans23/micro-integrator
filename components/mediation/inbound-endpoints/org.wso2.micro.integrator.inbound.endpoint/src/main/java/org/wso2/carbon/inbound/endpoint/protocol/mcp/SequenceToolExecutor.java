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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.json.JSONObject;

/**
 * Executes an MCP tool by injecting a message into a named Synapse sequence.
 *
 * <p>The tool arguments are set as the {@code mcp.tool.arguments} message context property
 * (JSON string). The sequence must set {@code mcp.tool.result} (JSON string) before
 * completing. If {@code mcp.tool.isError} is set to {@code "true"}, the result is
 * treated as an error.
 */
public class SequenceToolExecutor {

    private static final Log log = LogFactory.getLog(SequenceToolExecutor.class);

    private final org.apache.synapse.core.SynapseEnvironment synapseEnvironment;

    public SequenceToolExecutor(org.apache.synapse.core.SynapseEnvironment synapseEnvironment) {
        this.synapseEnvironment = synapseEnvironment;
    }

    /**
     * Executes the sequence-backed tool and returns the result string.
     *
     * @param tool tool descriptor with sequence binding details
     * @param args MCP tool arguments
     * @return result string (from {@code mcp.tool.result} property)
     * @throws McpToolExecutionException if the sequence is not found or throws an exception
     */
    public String execute(McpToolDescriptor tool, JSONObject args) throws McpToolExecutionException {
        String sequenceName = tool.getSequenceName();

        SequenceMediator sequence = (SequenceMediator) synapseEnvironment
                .getSynapseConfiguration().getSequence(sequenceName);
        if (sequence == null) {
            throw new McpToolExecutionException(
                    "Sequence '" + sequenceName + "' referenced by MCP tool '" + tool.getName() + "' is not deployed");
        }

        MessageContext msgCtx;
        try {
            msgCtx = createMessageContext(tool.getName(), args);
        } catch (AxisFault e) {
            throw new McpToolExecutionException(
                    "Failed to create message context for MCP tool '" + tool.getName() + "'", e);
        }

        if (log.isDebugEnabled()) {
            log.debug("MCP tool '" + tool.getName() + "' → injecting into sequence '" + sequenceName + "'");
        }

        try {
            // sequential=true: run on the current worker thread so we can read the result
            synapseEnvironment.injectMessage(msgCtx, sequence);
        } catch (SynapseException e) {
            throw new McpToolExecutionException(
                    "Sequence '" + sequenceName + "' threw an exception for MCP tool '" + tool.getName() + "'", e);
        }

        Object result = msgCtx.getProperty(McpConstants.MC_PROPERTY_TOOL_RESULT);
        if (result == null) {
            log.warn("MCP sequence tool '" + tool.getName() + "' did not set the '"
                    + McpConstants.MC_PROPERTY_TOOL_RESULT + "' property — returning empty result");
            return "";
        }
        return result.toString();
    }

    /**
     * Returns {@code true} if the sequence set {@code mcp.tool.isError=true}.
     */
    public static boolean isError(MessageContext msgCtx) {
        return "true".equalsIgnoreCase(String.valueOf(msgCtx.getProperty(McpConstants.MC_PROPERTY_TOOL_IS_ERROR)));
    }

    private MessageContext createMessageContext(String toolName, JSONObject args) throws AxisFault {
        MessageContext msgCtx = synapseEnvironment.createMessageContext();

        org.apache.axis2.context.MessageContext axis2Ctx =
                ((Axis2MessageContext) msgCtx).getAxis2MessageContext();
        axis2Ctx.setServerSide(true);
        axis2Ctx.setMessageID(org.apache.axiom.util.UIDGenerator.generateUID());

        // Build an empty SOAP envelope as the message body carrier
        SOAPFactory soapFactory = OMAbstractFactory.getSOAP12Factory();
        SOAPEnvelope envelope = soapFactory.getDefaultEnvelope();
        try {
            msgCtx.setEnvelope(envelope);
        } catch (AxisFault e) {
            throw e;
        }

        msgCtx.setProperty(SynapseConstants.IS_INBOUND, true);
        msgCtx.setProperty(McpConstants.MC_PROPERTY_TOOL_NAME, toolName);
        msgCtx.setProperty(McpConstants.MC_PROPERTY_TOOL_ARGUMENTS, args.toString());

        return msgCtx;
    }
}
