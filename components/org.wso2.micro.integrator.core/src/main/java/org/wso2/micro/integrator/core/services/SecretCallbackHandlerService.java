package org.wso2.micro.integrator.core.services;

import org.wso2.securevault.secret.SecretCallbackHandler;

/**
 * Expose <code>SecretCallbackHandler</code> as a service
 */
public interface SecretCallbackHandlerService {

    /**
     * Returns the global secret call handler
     *
     * @return An instance of <code>SecretCallbackHandler</code>
     */
    SecretCallbackHandler getSecretCallbackHandler();

    /**
     * Register the global secret call handler
     *
     * @param secretCallbackHandler an instance of <code>SecretCallbackHandler</code>
     */
    void setSecretCallbackHandler(SecretCallbackHandler secretCallbackHandler);
}

