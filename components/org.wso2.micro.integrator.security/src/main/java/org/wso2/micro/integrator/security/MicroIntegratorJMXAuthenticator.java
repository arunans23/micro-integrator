/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.micro.integrator.security;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.security.user.api.UserStoreException;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;
import java.util.Collections;

public class MicroIntegratorJMXAuthenticator implements JMXAuthenticator {
    private static Log log = LogFactory.getLog(MicroIntegratorJMXAuthenticator.class);

    @Override
    public Subject authenticate(Object credentials) {
        // Verify that credentials is of type String[].
        //
        if (!(credentials instanceof String[])) {
            // Special case for null so we get a more informative message
            if (credentials == null) {
                throw new SecurityException("Credentials required");
            }
            throw new SecurityException("Credentials should be String[]");
        }

        // Verify that the array contains username/password
        //
        final String[] aCredentials = (String[]) credentials;
        if (aCredentials.length < 2) {
            throw new SecurityException("Credentials should have at least username & password");
        }

        // Perform authentication
        //
        String userName = aCredentials[0];
        String password = aCredentials[1];

        try {
            if (MicroIntegratorSecurityUtils.getUserStoreManager().authenticate(userName, password)){
                return new Subject(true,
                        Collections.singleton(new JMXPrincipal(userName)),
                        Collections.EMPTY_SET,
                        Collections.EMPTY_SET);
            };
        } catch (UserStoreException e) {
            log.error("Error in authenticating user", e);
            return null;
        }
        return null;
    }
}
