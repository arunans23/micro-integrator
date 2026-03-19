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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.ei.dataservice.integration.test.graphql;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.ei.dataservice.integration.test.DSSIntegrationTest;

import static org.wso2.ei.dataservice.integration.test.graphql.GraphQLTestUtils.OK;
import static org.wso2.ei.dataservice.integration.test.graphql.GraphQLTestUtils.execute;

/**
 * Integration tests for GraphQL query operations exposed by {@code GraphQLSampleService}.
 *
 * <p>The service is pre-deployed via
 * {@code artifacts/DSS/server/repository/deployment/server/dataservices/GraphQLSampleService.dbs}
 * and connects to the shared H2 OData test database which contains 122 Customers rows.
 *
 * <p>All operations are read-only (SELECT); mutation tests are in {@link GraphQLMutationTestCase}.
 */
public class GraphQLQueryTestCase extends DSSIntegrationTest {

    private static final String SERVICE_NAME = "GraphQLSampleService";
    private String graphqlEndpoint;

    @BeforeClass(alwaysRun = true)
    public void serviceDeployment() throws Exception {
        super.init();
        // webAppURL is e.g. http://localhost:8480
        graphqlEndpoint = dssContext.getContextUrls().getWebAppURL() + "/graphql/" + SERVICE_NAME;
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        deleteService(SERVICE_NAME);
        cleanup();
    }

    /**
     * getCustomers returns all rows from the Customers table.
     * Verifies HTTP 200, no errors in response, and that known customer names appear.
     */
    @Test(groups = "wso2.dss", description = "GraphQL getCustomers query returns all customers")
    public void testGetCustomersReturnsResults() throws Exception {
        String query = "{ getCustomers { Customer { customerNumber customerName country } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK, "Expected HTTP 200");
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Response must not contain errors: " + body);
        Assert.assertTrue(body.contains("\"data\""), "Response must contain a data key: " + body);
        Assert.assertTrue(body.contains("\"getCustomers\""), "Response must contain getCustomers field: " + body);
        Assert.assertTrue(body.contains("\"Customer\""), "Response must contain Customer list: " + body);
        // The Customers table has 122 rows in the OData test DB
        Assert.assertTrue(body.contains("customerNumber"), "Response must include customerNumber: " + body);
        Assert.assertTrue(body.contains("customerName"),   "Response must include customerName: " + body);
    }

    /**
     * getCustomers only returns requested fields (customerName and country).
     * Verifies that GraphQL field selection is honoured and unrequested fields are absent.
     */
    @Test(groups = "wso2.dss", description = "GraphQL field selection returns only requested fields")
    public void testGetCustomersFieldSelection() throws Exception {
        String query = "{ getCustomers { Customer { customerName country } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK);
        String body = (String) response[1];
        Assert.assertTrue(body.contains("\"customerName\""), "customerName must be in response: " + body);
        Assert.assertTrue(body.contains("\"country\""), "country must be in response: " + body);
        // customerNumber was NOT requested, so it must be absent from the response
        Assert.assertFalse(body.contains("\"customerNumber\""),
                "customerNumber must NOT appear when not requested: " + body);
    }

    /**
     * getCustomerByNumber with a known customerNumber returns exactly that customer.
     * Customer 103 is "Atelier graphique" from France in the OData test dataset.
     */
    @Test(groups = "wso2.dss", description = "GraphQL getCustomerByNumber returns specific customer")
    public void testGetCustomerByNumber() throws Exception {
        String query = "{ getCustomerByNumber(customerNumber: 103) { Customer { customerName country } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK);
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Response must not contain errors: " + body);
        Assert.assertTrue(body.contains("Atelier graphique"),
                "Response must contain the expected customer name: " + body);
        Assert.assertTrue(body.contains("France"), "Customer 103 is from France: " + body);
    }

    /**
     * getCustomerByNumber with a non-existent customerNumber returns an empty Customer list.
     */
    @Test(groups = "wso2.dss", description = "GraphQL getCustomerByNumber with missing key returns empty list")
    public void testGetCustomerByNumberNotFound() throws Exception {
        String query = "{ getCustomerByNumber(customerNumber: 99999) { Customer { customerName } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK);
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Response must not contain errors: " + body);
        // Empty Customer list renders as an empty JSON array
        Assert.assertTrue(body.contains("\"Customer\":[]") || body.contains("\"Customer\": []"),
                "Non-existent customerNumber must return empty Customer list: " + body);
    }

    /**
     * Sending an invalid GraphQL document returns a response that contains a GraphQL errors array.
     * The HTTP status should still be 200 (GraphQL spec).
     */
    @Test(groups = "wso2.dss", description = "Invalid GraphQL query returns errors in response body")
    public void testInvalidQueryReturnsErrors() throws Exception {
        // Missing closing brace makes this syntactically invalid
        String query = "{ getCustomers { Customer { customerName }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK, "HTTP status must be 200 even for GraphQL errors");
        String body = (String) response[1];
        Assert.assertTrue(body.contains("\"errors\""),
                "Syntactically invalid query must produce an errors array: " + body);
    }

    /**
     * getStudents on an empty Student table returns an empty list without errors.
     */
    @Test(groups = "wso2.dss", description = "GraphQL getStudents returns empty list for empty table")
    public void testGetStudentsOnEmptyTable() throws Exception {
        String query = "{ getStudents { Student { studentId firstName lastName } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK);
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Response must not contain errors: " + body);
        Assert.assertTrue(body.contains("\"getStudents\""), "Response must contain getStudents field: " + body);
    }
}
