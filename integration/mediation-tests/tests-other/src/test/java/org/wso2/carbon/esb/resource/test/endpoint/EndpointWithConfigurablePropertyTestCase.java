/*
 *  Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.esb.resource.test.endpoint;

import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.carbon.integration.common.utils.exceptions.AutomationUtilException;
import org.wso2.esb.integration.common.utils.CarbonLogReader;
import org.wso2.esb.integration.common.utils.ESBIntegrationTest;
import org.wso2.esb.integration.common.utils.Utils;
import org.wso2.esb.integration.common.utils.common.ServerConfigurationManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertTrue;

public class EndpointWithConfigurablePropertyTestCase extends ESBIntegrationTest {

    private ServerConfigurationManager serverConfigurationManager;

    @BeforeClass(alwaysRun = true)
    public void init() throws Exception {
        super.init();
        CarbonLogReader carbonLogReader = new CarbonLogReader();
        carbonLogReader.start();
        serverConfigurationManager = new ServerConfigurationManager(context);
        File capp = new File(getESBResourceLocation() + File.separator + "config.var" + File.separator +
                "testConfiguration_1.0.0.car");
        serverConfigurationManager.copyToCarbonapps(capp);
        assertTrue(Utils.checkForLog(carbonLogReader, "Successfully Deployed Carbon Application : " +
                "testConfiguration_1.0.0", 20), "Did not receive the expected info log");
    }

    @Test(groups = {"wso2.esb"}, description = "Configurable property", priority = 1)
    public void testConfigurablePropertyWithFile() throws IOException {
        Map<String, String> headers = new HashMap<>();
        URL endpoint = new URL(getApiInvocationURL("getdata/file"));
        HttpResponse httpResponse = HttpRequestUtil.doGet(endpoint.toString(), headers);
        Assert.assertEquals(httpResponse.getResponseCode(), 200);
        Assert.assertEquals(httpResponse.getData(), "{\"msg\": \"file\"}", httpResponse.getData());
    }

    @Test(groups = {"wso2.esb"}, description = "Configurable property", priority = 2)
    public void testConfigurablePropertyWithSystemProperty() throws IOException, AutomationUtilException {
        Map<String, String> commands = new HashMap<>();
        commands.put("-Dendpoint_url", "http://localhost:8480/endpoint/sys");
        commands.put("-Dcommon_url", "http://localhost:8480/endpoint/sys");
        commands.put("-Durl", "http://localhost:8480/endpoint/sys");
        commands.put("-Durl_value", "http://localhost:8480/endpoint/sys");
        serverConfigurationManager.restartMicroIntegrator(commands);
        Map<String, String> headers = new HashMap<>();
        URL endpoint = new URL(getApiInvocationURL("getdata/sys"));
        HttpResponse httpResponse = HttpRequestUtil.doGet(endpoint.toString(), headers);
        Assert.assertEquals(httpResponse.getResponseCode(), 200);
        Assert.assertEquals(httpResponse.getData(), "{\"msg\": \"sys\"}", httpResponse.getData());
    }

    @Test(groups = {"wso2.esb"}, description = "Configurable property", priority = 3)
    public void testConfigurablePropertyWithEnvVariable() throws IOException, AutomationUtilException {
        Map<String, String> commands = new HashMap<>();
        commands.put("--env-file", FrameworkPathUtil.getSystemResourceLocation() + "test.env");
        serverConfigurationManager.restartMicroIntegrator(commands);
        Map<String, String> headers = new HashMap<>();
        URL endpoint = new URL(getApiInvocationURL("getdata/env"));
        HttpResponse httpResponse = HttpRequestUtil.doGet(endpoint.toString(), headers);
        Assert.assertEquals(httpResponse.getResponseCode(), 200);
        Assert.assertEquals(httpResponse.getData(), "{\"msg\": \"env\"}", httpResponse.getData());
    }

    @Test(groups = {"wso2.esb"}, description = "Configurable property", priority = 4)
    public void testConfigurableProperty() throws IOException, AutomationUtilException {
        Map<String, String> commands = new HashMap<>();
        commands.put("-Dcommon_url", "http://localhost:8480/endpoint/sys");
        commands.put("--env-file", FrameworkPathUtil.getSystemResourceLocation() + "test.env");
        serverConfigurationManager.restartMicroIntegrator(commands);
        Map<String, String> headers = new HashMap<>();
        URL endpoint = new URL(getApiInvocationURL("getdata/common"));
        HttpResponse httpResponse = HttpRequestUtil.doGet(endpoint.toString(), headers);
        Assert.assertEquals(httpResponse.getResponseCode(), 200);
        Assert.assertEquals(httpResponse.getData(), "{\"msg\": \"hi\"}", httpResponse.getData());
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        serverConfigurationManager.removeFromCarbonapps("testConfiguration_1.0.0.car");
        super.cleanup();
    }
}
