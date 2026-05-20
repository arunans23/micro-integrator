/*
 * Copyright (c) 2026, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.esb.car.deployment.test;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Verifies the full case-sensitive alphabetical ordering chain when priority deployment is
 * <em>disabled</em>.
 *
 * <p>Java's {@link String#compareTo} uses Unicode code point ordering:
 * {@code 'A'}(65) &lt; {@code 'Z'}(90) &lt; {@code 'a'}(97) &lt; {@code 'z'}(122).
 * All four CApps used in this test begin with a different letter from this chain, so
 * the expected deployment order is:
 * <ol>
 *   <li>{@code A_B_PlainApiACApp} — uppercase {@code 'A'} (65)</li>
 *   <li>{@code Z_A_ClassMediatorCApp} — uppercase {@code 'Z'} (90)</li>
 *   <li>{@code a_CaseSensitiveLowCApp} — lowercase {@code 'a'} (97)</li>
 *   <li>{@code z_CaseSensitiveLowCApp} — lowercase {@code 'z'} (122)</li>
 * </ol>
 *
 * <p>The two case-sensitive proxy CApps ({@code a_*} and {@code z_*}) reference
 * {@code org.wso2.esb.TestMediator}. Because the class mediator CApp ({@code Z_A_*}) deploys
 * second (before both lowercase CApps), the mediator class is available when those proxies
 * deploy and both succeed.
 */
public class CAppCaseSensitiveMixedFullOrderDisabledTestCase extends CAppPriorityDeploymentTestBase {

    // =========================================================================
    // Setup / Teardown
    // =========================================================================

    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
        startServer(new File(PRIORITY_DISABLED_TOML),
                PLAIN_API_A_CAPP, CLASS_MEDIATOR_CAPP, LOWER_A_CASE_CAPP, LOWER_Z_CASE_CAPP);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        stopServer(PLAIN_API_A_CAPP, CLASS_MEDIATOR_CAPP, LOWER_A_CASE_CAPP, LOWER_Z_CASE_CAPP);
    }

    // =========================================================================
    // Test 1 – Server started successfully
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "Server must complete startup when testing mixed case-sensitive full ordering")
    public void testServerStartedSuccessfully() {
        Assert.assertTrue(startupLogs.contains(SERVER_STARTED_LOG),
                "Server startup log not found — MI may not have started correctly. " +
                        "Expected to find: \"" + SERVER_STARTED_LOG + "\"");
    }

    // =========================================================================
    // Test 2 – All four CApps deployed successfully
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "All four CApps must deploy successfully — the class mediator must be on the classpath " +
            "when the two lowercase-prefix proxy CApps deploy",
            dependsOnMethods = "testServerStartedSuccessfully")
    public void testAllCAppsDeployedSuccessfully() {
        String[] cApps = {PLAIN_API_A_CAPP, CLASS_MEDIATOR_CAPP, LOWER_A_CASE_CAPP, LOWER_Z_CASE_CAPP};
        for (String capp : cApps) {
            Assert.assertTrue(startupLogs.contains(DEPLOYED_LOG_PREFIX + capp),
                    "CApp was not deployed successfully: " + capp);
            Assert.assertFalse(startupLogs.contains(FAILED_LOG_PREFIX + capp),
                    "Unexpected deployment failure for: " + capp);
        }
    }

    // =========================================================================
    // Test 3 – Full Unicode chain: 'A'(65) < 'Z'(90) < 'a'(97) < 'z'(122)
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "CApps must deploy in strict Unicode order: A_(65) → Z_(90) → a_(97) → z_(122), " +
            "confirming case-sensitive compareTo ordering is used",
            dependsOnMethods = "testAllCAppsDeployedSuccessfully")
    public void testFullUnicodeChainOrder() {
        int idxA       = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + PLAIN_API_A_CAPP);
        int idxUpperZ  = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + CLASS_MEDIATOR_CAPP);
        int idxLowerA  = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + LOWER_A_CASE_CAPP);
        int idxLowerZ  = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + LOWER_Z_CASE_CAPP);

        Assert.assertTrue(idxA < idxUpperZ,
                "A_B_PlainApiACApp ('A'=65) must deploy before Z_A_ClassMediatorCApp ('Z'=90). " +
                        "idxA=" + idxA + ", idxUpperZ=" + idxUpperZ);
        Assert.assertTrue(idxUpperZ < idxLowerA,
                "Z_A_ClassMediatorCApp ('Z'=90) must deploy before a_CaseSensitiveLowCApp ('a'=97). " +
                        "idxUpperZ=" + idxUpperZ + ", idxLowerA=" + idxLowerA);
        Assert.assertTrue(idxLowerA < idxLowerZ,
                "a_CaseSensitiveLowCApp ('a'=97) must deploy before z_CaseSensitiveLowCApp ('z'=122). " +
                        "idxLowerA=" + idxLowerA + ", idxLowerZ=" + idxLowerZ);
    }
}
