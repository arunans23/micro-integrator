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
 * Verifies the {@code priority_deployment_high_priority_types} configuration, which lets operators
 * override which artifact types are treated as high-priority.
 *
 * <p>TOML: {@code enable_priority_deployment = true},
 * {@code priority_deployment_high_priority_types = ["datasource/datasource"]}.
 *
 * <p>Setup: Four CApps are deployed:
 * <ul>
 *   <li>{@code TestDataSource_1.0.0} — <b>high-priority</b> ({@code datasource/datasource}
 *       is the only type in the custom list)</li>
 *   <li>{@code Z_A_ClassMediatorCApp} — <b>low-priority</b> ({@code lib/synapse/mediator} is
 *       no longer in the custom list)</li>
 *   <li>{@code A_A_DependentProxyCApp} — <b>low-priority</b> (depends on the class mediator)</li>
 *   <li>{@code A_B_PlainApiACApp} — <b>low-priority</b></li>
 * </ul>
 *
 * <p>This scenario exercises three key behaviours in a single server start:
 * <ol>
 *   <li><b>Custom type is honoured:</b> the datasource CApp is treated as high-priority and
 *       deploys first.</li>
 *   <li><b>Default types are overridden:</b> the class mediator CApp ({@code lib/synapse/mediator})
 *       is demoted to low-priority because {@code lib/synapse/mediator} is absent from the custom
 *       list. In the low-priority pass it sorts alphabetically as {@code Z_A_*}, which is after
 *       {@code A_A_*} and {@code A_B_*}.</li>
 *   <li><b>Demotion causes a failure:</b> {@code A_A_DependentProxyCApp} sorts before
 *       {@code Z_A_ClassMediatorCApp} alphabetically ({@code 'A'=65 < 'Z'=90}), so the proxy
 *       deploys before the mediator class is on the classpath and fails.</li>
 * </ol>
 */
public class CAppCustomHighPriorityTypesTestCase extends CAppPriorityDeploymentTestBase {

    // =========================================================================
    // Setup / Teardown
    // =========================================================================

    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
        startServer(new File(PRIORITY_CUSTOM_TYPES_DS_ONLY_TOML),
                DATASOURCE_CAPP, CLASS_MEDIATOR_CAPP,
                DEPENDENT_PROXY_CAPP, PLAIN_API_A_CAPP);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        stopServer(DATASOURCE_CAPP, CLASS_MEDIATOR_CAPP,
                DEPENDENT_PROXY_CAPP, PLAIN_API_A_CAPP);
    }

    // =========================================================================
    // Test 1 – Server started successfully
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "Server must complete startup even though the dependent proxy fails")
    public void testServerStartedSuccessfully() {
        Assert.assertTrue(startupLogs.contains(SERVER_STARTED_LOG),
                "Server startup log not found — MI may not have started correctly. " +
                        "Expected to find: \"" + SERVER_STARTED_LOG + "\"");
    }

    // =========================================================================
    // Test 2 – Custom type is honoured: datasource CApp is high-priority
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "The datasource CApp must deploy successfully as the sole high-priority CApp and " +
            "must appear in the log before both low-priority CApps",
            dependsOnMethods = "testServerStartedSuccessfully")
    public void testDatasourceCAppIsHighPriority() {
        Assert.assertTrue(startupLogs.contains(DEPLOYED_LOG_PREFIX + DATASOURCE_CAPP),
                "Datasource CApp was not deployed: " + DATASOURCE_CAPP);
        Assert.assertFalse(startupLogs.contains(FAILED_LOG_PREFIX + DATASOURCE_CAPP),
                "Unexpected failure for datasource CApp: " + DATASOURCE_CAPP);

        int datasourceIdx = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + DATASOURCE_CAPP);
        int mediatorIdx   = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + CLASS_MEDIATOR_CAPP);
        int plainApiAIdx  = startupLogs.indexOf(DEPLOYED_LOG_PREFIX + PLAIN_API_A_CAPP);

        Assert.assertTrue(datasourceIdx < mediatorIdx,
                DATASOURCE_CAPP + " (custom high-priority) must deploy before " +
                        CLASS_MEDIATOR_CAPP + " (now low-priority). datasourceIdx=" +
                        datasourceIdx + ", mediatorIdx=" + mediatorIdx);
        Assert.assertTrue(datasourceIdx < plainApiAIdx,
                DATASOURCE_CAPP + " (custom high-priority) must deploy before " +
                        PLAIN_API_A_CAPP + ". datasourceIdx=" + datasourceIdx +
                        ", plainApiAIdx=" + plainApiAIdx);
    }

    // =========================================================================
    // Test 3 – Default type overridden: class mediator is now low-priority
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "The class mediator CApp (lib/synapse/mediator) must deploy because it has no external " +
            "dependencies, but it must be treated as low-priority — it appears in the log after " +
            "the datasource CApp (high-priority)",
            dependsOnMethods = "testDatasourceCAppIsHighPriority")
    public void testClassMediatorDemotedToLowPriority() {
        Assert.assertTrue(startupLogs.contains(DEPLOYED_LOG_PREFIX + CLASS_MEDIATOR_CAPP),
                CLASS_MEDIATOR_CAPP + " was not deployed");
    }

    // =========================================================================
    // Test 4 – Demotion causes failure: dependent proxy fails because mediator is now low-priority
    // =========================================================================

    @Test(groups = {"wso2.esb"}, description =
            "A_A_DependentProxyCApp must fail: it is low-priority and sorts before " +
            "Z_A_ClassMediatorCApp alphabetically ('A'=65 < 'Z'=90), so the mediator class is " +
            "not on the classpath when the proxy is deployed",
            dependsOnMethods = "testServerStartedSuccessfully")
    public void testDependentProxyFailsDueToMediatorDemotion() {
        Assert.assertTrue(
                startupLogs.contains(FAILED_LOG_PREFIX + DEPENDENT_PROXY_CAPP) ||
                startupLogs.contains("Successfully undeployed Carbon Application : " + DEPENDENT_PROXY_CAPP),
                DEPENDENT_PROXY_CAPP + " must fail because lib/synapse/mediator is no longer " +
                        "high-priority in the custom type list");
        Assert.assertFalse(startupLogs.contains(DEPLOYED_LOG_PREFIX + DEPENDENT_PROXY_CAPP),
                DEPENDENT_PROXY_CAPP + " must not appear in the success log");
    }
}
