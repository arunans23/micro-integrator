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

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMOutputFormat;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.axis2.transport.MessageFormatter;
import org.apache.axis2.transport.TransportSender;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.protocol.HTTP;
import org.apache.synapse.SynapseException;
import org.apache.synapse.transport.nhttp.util.MessageFormatterDecoratorFactory;
import org.apache.synapse.transport.passthru.PassThroughConstants;
import org.apache.synapse.transport.passthru.PassThroughHttpSender;
import org.apache.synapse.transport.passthru.Pipe;
import org.apache.synapse.transport.passthru.ProtocolState;
import org.apache.synapse.transport.passthru.SourceContext;
import org.apache.synapse.transport.passthru.SourceRequest;
import org.apache.synapse.transport.passthru.SourceResponse;
import org.apache.synapse.transport.passthru.config.SourceConfiguration;
import org.apache.synapse.transport.passthru.util.PassThroughTransportUtils;
import org.apache.synapse.transport.passthru.util.SourceResponseFactory;
import org.wso2.micro.integrator.core.Constants;

import java.io.IOException;
import java.io.OutputStream;
import java.rmi.RemoteException;
import javax.xml.namespace.QName;

/**
 * Sends a GraphQL JSON response back to the HTTP client via the PassThrough transport.
 * Adapted from {@code ODataTransportSender}.
 */
public class GraphQLTransportSender extends PassThroughHttpSender implements TransportSender {

    private static final String PASS_THROUGH_SOURCE_CONNECTION = "pass-through.Source-Connection";
    private static final String PASS_THROUGH_SOURCE_CONFIGURATION = "PASS_THROUGH_SOURCE_CONFIGURATION";
    private static final String RESPONSE_MESSAGE_CONTEXT = "RESPONSE_MESSAGE_CONTEXT";
    private static final String CONTENT_TYPE = "ContentType";
    private static final String APPLICATION_JSON = "application/json";

    private GraphQLServletResponse response;

    public GraphQLTransportSender(ConfigurationContext configurationContext,
                                  TransportOutDescription transportOut,
                                  GraphQLServletResponse response) {
        this.response = response;
        try {
            init(configurationContext, transportOut);
        } catch (AxisFault e) {
            throw new SynapseException("Error initializing GraphQL transport sender.", e);
        }
    }

    public void setResponse(GraphQLServletResponse response) {
        this.response = response;
    }

    @Override
    public void submitResponse(MessageContext msgContext) throws IOException {
        SourceConfiguration sourceConfiguration = (SourceConfiguration) msgContext.getProperty(
                PASS_THROUGH_SOURCE_CONFIGURATION);
        NHttpServerConnection conn = (NHttpServerConnection) msgContext.getProperty(PASS_THROUGH_SOURCE_CONNECTION);
        SourceRequest sourceRequest = SourceContext.getRequest(conn);
        if (sourceRequest == null) {
            if (conn.getContext().getAttribute(PassThroughConstants.SOURCE_CONNECTION_DROPPED) == null
                    || !(Boolean) conn.getContext().getAttribute(PassThroughConstants.SOURCE_CONNECTION_DROPPED)) {
                this.log.warn("Trying to submit a GraphQL response to an already closed connection: " + conn);
            }
            return;
        }

        SourceResponse sourceResponse = SourceResponseFactory.create(msgContext, sourceRequest, sourceConfiguration);
        conn.getContext().setAttribute(RESPONSE_MESSAGE_CONTEXT, msgContext);
        SourceContext.setResponse(conn, sourceResponse);

        Boolean noEntityBody = (Boolean) msgContext.getProperty(PassThroughConstants.NO_ENTITY_BODY);
        Pipe pipe = (Pipe) msgContext.getProperty(PassThroughConstants.PASS_THROUGH_PIPE);

        if (noEntityBody == null || !noEntityBody || pipe != null) {
            if (pipe == null) {
                pipe = new Pipe(sourceConfiguration.getBufferFactory().getBuffer(), "Pipe", sourceConfiguration);
                msgContext.setProperty(PassThroughConstants.PASS_THROUGH_PIPE, pipe);
                msgContext.setProperty(PassThroughConstants.MESSAGE_BUILDER_INVOKED, Boolean.TRUE);
            }
            pipe.attachConsumer(conn);
            sourceResponse.connect(pipe);
        }

        Integer errorCode = (Integer) msgContext.getProperty(PassThroughConstants.ERROR_CODE);
        if (errorCode != null) {
            sourceResponse.setStatus(Constants.BAD_GATEWAY);
            SourceContext.get(conn).setShutDown(true);
        }

        ProtocolState state = SourceContext.getState(conn);
        if (state != null && state.compareTo(ProtocolState.REQUEST_DONE) <= 0) {
            if (msgContext.isPropertyTrue(PassThroughConstants.MESSAGE_BUILDER_INVOKED) && pipe != null) {
                OutputStream out = pipe.getOutputStream();
                MessageFormatter formatter = MessageFormatterDecoratorFactory.createMessageFormatterDecorator(
                        msgContext);
                OMOutputFormat format = PassThroughTransportUtils.getOMOutputFormat(msgContext);
                try {
                    // Write once (not streaming)
                    setMessageContent(msgContext, response);
                    initResponse(msgContext, response, formatter, format, sourceResponse);
                    formatter.writeTo(msgContext, format, out, false);
                } catch (RemoteException e) {
                    IOUtils.closeQuietly(out);
                    throw new SynapseException("Error building GraphQL message context.", e);
                } finally {
                    pipe.setSerializationComplete(true);
                    out.close();
                    response.forceComplete();
                }
            }
            conn.requestOutput();
        } else {
            if (errorCode != null) {
                this.log.warn("GraphQL source connection is closed due to a target error: " + conn);
            } else {
                this.log.debug("GraphQL source connection is closed; already writing a response: " + conn);
            }
            pipe.consumerError();
            SourceContext.updateState(conn, ProtocolState.CLOSED);
            sourceConfiguration.getSourceConnections().shutDownConnection(conn, true);
        }
    }

    private void initResponse(MessageContext msgContext, GraphQLServletResponse response,
                              MessageFormatter formatter, OMOutputFormat format,
                              SourceResponse sourceResponse) {
        msgContext.setProperty(PassThroughConstants.HTTP_SC, response.getStatus());
        msgContext.setProperty(CONTENT_TYPE, APPLICATION_JSON);
        setContentType(msgContext, sourceResponse, formatter, format);
        sourceResponse.setStatus(response.getStatus());
    }

    private void setMessageContent(MessageContext axis2MessageContext,
                                   GraphQLServletResponse response) throws AxisFault {
        String content = response.getContentAsString();
        if (content == null) {
            content = StringUtils.EMPTY;
        }
        SOAPFactory fac = OMAbstractFactory.getSOAP11Factory();
        SOAPEnvelope envelope = fac.getDefaultEnvelope();
        envelope.getBody().addChild(createTextElement(content));
        axis2MessageContext.setEnvelope(envelope);
    }

    private OMElement createTextElement(String content) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMElement textElement = factory.createOMElement(
                new QName("http://ws.apache.org/commons/ns/payload", "text"));
        textElement.setText(content != null ? content : StringUtils.EMPTY);
        return textElement;
    }

    public void setContentType(MessageContext msgContext, SourceResponse sourceResponse,
                               MessageFormatter formatter, OMOutputFormat format) {
        Object contentTypeInMsgCtx = msgContext.getProperty(CONTENT_TYPE);
        boolean isContentTypeSetFromMsgCtx = false;
        if (contentTypeInMsgCtx != null) {
            String contentTypeValue = contentTypeInMsgCtx.toString();
            if (!contentTypeValue.contains(PassThroughConstants.CONTENT_TYPE_MULTIPART_RELATED)
                    && !contentTypeValue.contains(PassThroughConstants.CONTENT_TYPE_MULTIPART_FORM_DATA)) {
                if (format != null && contentTypeValue.indexOf(HTTPConstants.CHAR_SET_ENCODING) == -1
                        && !"false".equals(msgContext.getProperty(PassThroughConstants.SET_CHARACTER_ENCODING))) {
                    String encoding = format.getCharSetEncoding();
                    if (encoding != null) {
                        contentTypeValue = contentTypeValue + "; charset=" + encoding;
                    }
                }
                sourceResponse.removeHeader(HTTP.CONTENT_TYPE);
                sourceResponse.addHeader(HTTP.CONTENT_TYPE, contentTypeValue);
                isContentTypeSetFromMsgCtx = true;
            }
        }
        if (!isContentTypeSetFromMsgCtx) {
            sourceResponse.removeHeader(HTTP.CONTENT_TYPE);
            sourceResponse.addHeader(HTTP.CONTENT_TYPE,
                    formatter.getContentType(msgContext, format, msgContext.getSoapAction()));
        }
    }
}
