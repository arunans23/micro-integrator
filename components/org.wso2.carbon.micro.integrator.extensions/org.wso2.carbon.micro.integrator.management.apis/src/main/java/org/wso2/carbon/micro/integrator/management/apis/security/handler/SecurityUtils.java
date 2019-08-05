/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.micro.integrator.management.apis.security.handler;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.crypto.CryptoConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.wso2.securevault.SecretResolver;
import org.wso2.securevault.SecureVaultException;

import java.util.Map;
import javax.xml.namespace.QName;

public class SecurityUtils {

    /**
     * Returns the transport header map of a given axis2 message context.
     *
     * @param axis2MessageContext axis2 message context
     * @return transport header map
     */
    public static Map getHeaders(org.apache.axis2.context.MessageContext axis2MessageContext) {

        Object headers = axis2MessageContext.getProperty(
                org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        Map headersMap = null;
        if (headers != null && headers instanceof Map) {
            headersMap = (Map) headers;
        }
        return headersMap;
    }

    /**
     * Sets the provided status code for the response.
     *
     * @param messageContext Synapse message context
     * @param statusCode     status code
     */
    public static void setStatusCode(MessageContext messageContext, int statusCode) {

        org.apache.axis2.context.MessageContext axis2MessageContext
                = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
        axis2MessageContext.setProperty(AuthConstants.HTTP_STATUS_CODE, statusCode);
        axis2MessageContext.setProperty(AuthConstants.NO_ENTITY_BODY, true);
        messageContext.setProperty(AuthConstants.RESPONSE, AuthConstants.TRUE);
        messageContext.setTo(null);
    }

    /**
     * Returns the resolved secret.
     *
     * @param secretResolver secret resolver initialized with relevant OM element
     * @param paramElement   OMElement password
     * @return resolved password
     */
    public static String getSecureVaultValue(SecretResolver secretResolver, OMElement paramElement) {
        String value = null;
        if (paramElement != null) {
            OMAttribute attribute = paramElement.getAttribute(new QName(CryptoConstants.SECUREVAULT_NAMESPACE,
                    CryptoConstants.SECUREVAULT_ALIAS_ATTRIBUTE));
            if (attribute != null && attribute.getAttributeValue() != null && !attribute.getAttributeValue().isEmpty
                    ()) {
                if (secretResolver == null) {
                    throw new SecureVaultException("Cannot resolve secret password because axis2 secret resolver "
                            + "is null");
                }
                if (secretResolver.isTokenProtected(attribute.getAttributeValue())) {
                    value = secretResolver.resolve(attribute.getAttributeValue());
                }
            } else {
                value = paramElement.getText();
            }
        }
        return value;
    }
}
