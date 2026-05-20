/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.

 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.micro.integrator.mediation.security.vault;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.wso2.carbon.securevault.SecretCallbackHandlerService;
import org.wso2.micro.integrator.mediation.security.vault.external.ExternalVaultConfigLoader;

import java.io.File;

@Component(
        name = "mediation.security",
        immediate = true)
public class SynapseSecurityServiceComponent {

    private static Log log = LogFactory.getLog(SynapseSecurityServiceComponent.class);

    private static SecretCallbackHandlerService secretCallbackHandlerService;

    private Thread fileWatcherThread;

    @Activate
    protected void activate(ComponentContext ctxt) {
        log.debug("Synapse mediation security component is activated");
        ExternalVaultConfigLoader.loadExternalVaultConfigs(secretCallbackHandlerService);
        initializeRuntimeSecretStore();
    }

    @Deactivate
    protected void deactivate(ComponentContext ctxt) {
        log.debug("Synapse mediation security component is deactivated");
        if (fileWatcherThread != null) {
            fileWatcherThread.interrupt();
        }
    }

    private void initializeRuntimeSecretStore() {
        SecretVaultRuntimeManager runtimeManager = SecretVaultRuntimeManager.getInstance();
        String cipherTextPath = runtimeManager.getCipherTextPropertiesPath();

        if (cipherTextPath == null || !new File(cipherTextPath).exists()) {
            log.debug("cipher-text.properties not found; runtime secret store and file watcher not started");
            return;
        }

        try {
            runtimeManager.reloadFromFile();
        } catch (Exception e) {
            log.warn("Initial load of cipher-text.properties into runtime store failed; "
                    + "static FileBaseSecretRepository will serve as fallback", e);
        }

        CipherTextFileWatcher watcher = new CipherTextFileWatcher(cipherTextPath);
        fileWatcherThread = new Thread(watcher, "CipherTextFileWatcher");
        fileWatcherThread.setDaemon(true);
        fileWatcherThread.start();
        log.info("CipherTextFileWatcher started for cluster-wide secret sync");
    }

    @Reference(
            name = "secret.callback.handler.service",
            service = SecretCallbackHandlerService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetSecretCallbackHandlerService")
    protected void setSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {
        log.debug("SecretCallbackHandlerService bound to the ESB initialization process");
        this.secretCallbackHandlerService = secretCallbackHandlerService;
    }

    protected void unsetSecretCallbackHandlerService(SecretCallbackHandlerService secretCallbackHandlerService) {
        log.debug("SecretCallbackHandlerService unbound from the ESB environment");
        this.secretCallbackHandlerService = null;
    }
}
