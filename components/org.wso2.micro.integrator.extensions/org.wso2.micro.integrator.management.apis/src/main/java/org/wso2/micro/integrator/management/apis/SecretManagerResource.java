/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
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

package org.wso2.micro.integrator.management.apis;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapseConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.micro.integrator.management.apis.security.handler.SecurityUtils;
import org.wso2.micro.integrator.mediation.security.vault.SecretVaultRuntimeManager;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.wso2.micro.integrator.management.apis.Constants.BAD_REQUEST;
import static org.wso2.micro.integrator.management.apis.Constants.COUNT;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_DELETE;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_GET;
import static org.wso2.micro.integrator.management.apis.Constants.HTTP_POST;
import static org.wso2.micro.integrator.management.apis.Constants.INTERNAL_SERVER_ERROR;
import static org.wso2.micro.integrator.management.apis.Constants.LIST;
import static org.wso2.micro.integrator.management.apis.Constants.MESSAGE_JSON_ATTRIBUTE;
import static org.wso2.micro.integrator.management.apis.Constants.NOT_FOUND;
import static org.wso2.micro.integrator.management.apis.Constants.NOT_IMPLEMENTED;
import static org.wso2.micro.integrator.management.apis.Constants.USERNAME_PROPERTY;

/**
 * Management API resource for runtime secure vault secret management.
 *
 * GET    /management/secrets              — list all known alias names
 * POST   /management/secrets              — add or update a secret (upsert)
 * DELETE /management/secrets?alias=<key> — remove a secret
 *
 * The POST body must contain pre-encrypted ciphertext produced by the MI CLI
 * using the same keystore configured on this server. Plaintext is never accepted.
 */
public class SecretManagerResource implements MiApiResource {

    private static final Log LOG = LogFactory.getLog(SecretManagerResource.class);

    private static final String PARAM_ALIAS = "alias";
    private static final String PARAM_VALUE = "value";

    private final Set<String> methods;

    public SecretManagerResource() {
        methods = new HashSet<>();
        methods.add(HTTP_GET);
        methods.add(HTTP_POST);
        methods.add(HTTP_DELETE);
    }

    @Override
    public Set<String> getMethods() {
        return methods;
    }

    @Override
    public boolean invoke(MessageContext messageContext,
                          org.apache.axis2.context.MessageContext axis2MessageContext,
                          SynapseConfiguration synapseConfiguration) {

        if (!SecretVaultRuntimeManager.getInstance().isAvailable()) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError(
                            "The /management/secrets API only manages secrets stored in cipher-text.properties "
                                    + "(file-based secure vault). This server does not appear to be configured with "
                                    + "a file-based secret repository. If you are using an external vault provider "
                                    + "(e.g. AWS Secrets Manager, HashiCorp Vault), manage secrets directly through "
                                    + "that provider.",
                            axis2MessageContext, NOT_IMPLEMENTED));
            return true;
        }

        String httpMethod = (String) axis2MessageContext.getProperty(Constants.HTTP_METHOD_PROPERTY);

        if (HTTP_GET.equalsIgnoreCase(httpMethod)) {
            handleGet(axis2MessageContext);
        } else if (HTTP_POST.equalsIgnoreCase(httpMethod)) {
            String userName = (String) messageContext.getProperty(USERNAME_PROPERTY);
            try {
                if (SecurityUtils.canUserEdit(messageContext, userName)) {
                    handlePost(axis2MessageContext, synapseConfiguration);
                } else {
                    Utils.sendForbiddenFaultResponse(axis2MessageContext);
                }
            } catch (UserStoreException e) {
                LOG.error("Error retrieving user data", e);
                Utils.setJsonPayLoad(axis2MessageContext,
                        Utils.createJsonErrorObject("Error retrieving user data"));
            }
        } else if (HTTP_DELETE.equalsIgnoreCase(httpMethod)) {
            String userName = (String) messageContext.getProperty(USERNAME_PROPERTY);
            try {
                if (SecurityUtils.canUserEdit(messageContext, userName)) {
                    handleDelete(messageContext, axis2MessageContext, synapseConfiguration);
                } else {
                    Utils.sendForbiddenFaultResponse(axis2MessageContext);
                }
            } catch (UserStoreException e) {
                LOG.error("Error retrieving user data", e);
                Utils.setJsonPayLoad(axis2MessageContext,
                        Utils.createJsonErrorObject("Error retrieving user data"));
            }
        } else {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Method not allowed", axis2MessageContext, BAD_REQUEST));
        }

        return true;
    }

    private void handleGet(org.apache.axis2.context.MessageContext axis2MessageContext) {
        Set<String> aliases = SecretVaultRuntimeManager.getInstance().getAllAliases();
        JSONObject response = new JSONObject();
        response.put(COUNT, aliases.size());
        JSONArray list = new JSONArray(aliases);
        response.put(LIST, list);
        Utils.setJsonPayLoad(axis2MessageContext, response);
    }

    private void handlePost(org.apache.axis2.context.MessageContext axis2MessageContext,
                            SynapseConfiguration synapseConfiguration) {
        JsonObject payload;
        try {
            payload = Utils.getJsonPayload(axis2MessageContext);
        } catch (IOException e) {
            LOG.error("Failed to parse request payload", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Invalid JSON payload", axis2MessageContext, BAD_REQUEST));
            return;
        }

        if (!payload.has(PARAM_ALIAS) || !payload.has(PARAM_VALUE)) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Request must contain 'alias' and 'value' fields",
                            axis2MessageContext, BAD_REQUEST));
            return;
        }

        String alias = payload.get(PARAM_ALIAS).getAsString().trim();
        String encryptedValue = payload.get(PARAM_VALUE).getAsString().trim();

        if (alias.isEmpty() || encryptedValue.isEmpty()) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("'alias' and 'value' must not be empty",
                            axis2MessageContext, BAD_REQUEST));
            return;
        }

        try {
            SecretVaultRuntimeManager.getInstance().addOrUpdateSecret(alias, encryptedValue);
        } catch (SynapseException e) {
            LOG.error("Failed to decrypt ciphertext for alias '" + alias + "'", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Failed to decrypt the provided value. "
                                    + "Ensure it was encrypted with the server's configured keystore.",
                            axis2MessageContext, BAD_REQUEST));
            return;
        } catch (Exception e) {
            LOG.error("Failed to add/update secret for alias '" + alias + "'", e);
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Internal error while updating secret",
                            axis2MessageContext, INTERNAL_SERVER_ERROR));
            return;
        }

        // Invalidate cached decrypted value on this node so the next lookup picks up the new value
        synapseConfiguration.getDecryptedCacheMap().remove(alias);

        JSONObject response = new JSONObject();
        response.put(MESSAGE_JSON_ATTRIBUTE, "Secret '" + alias + "' updated successfully. "
                + "Other cluster nodes will pick up the change via the file watcher.");
        Utils.setJsonPayLoad(axis2MessageContext, response);
    }

    private void handleDelete(MessageContext messageContext,
                              org.apache.axis2.context.MessageContext axis2MessageContext,
                              SynapseConfiguration synapseConfiguration) {
        String alias = Utils.getQueryParameter(messageContext, PARAM_ALIAS);

        if (alias == null || alias.trim().isEmpty()) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Query parameter 'alias' is required",
                            axis2MessageContext, BAD_REQUEST));
            return;
        }

        alias = alias.trim();
        boolean removed = SecretVaultRuntimeManager.getInstance().removeSecret(alias);
        if (!removed) {
            Utils.setJsonPayLoad(axis2MessageContext,
                    Utils.createJsonError("Secret alias '" + alias + "' not found",
                            axis2MessageContext, NOT_FOUND));
            return;
        }

        synapseConfiguration.getDecryptedCacheMap().remove(alias);

        JSONObject response = new JSONObject();
        response.put(MESSAGE_JSON_ATTRIBUTE, "Secret '" + alias + "' removed successfully.");
        Utils.setJsonPayLoad(axis2MessageContext, response);
    }
}
