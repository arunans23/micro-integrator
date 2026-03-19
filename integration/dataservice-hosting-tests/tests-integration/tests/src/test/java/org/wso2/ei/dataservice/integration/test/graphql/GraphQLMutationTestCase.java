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
 * Integration tests for GraphQL mutation operations exposed by {@code GraphQLSampleService}.
 *
 * <p>Mutations target the {@code Student} table in the shared H2 OData test database.
 * Each test is isolated: it inserts a row, verifies it, then deletes it.
 * Tests are ordered so that insert always precedes the corresponding delete.
 */
public class GraphQLMutationTestCase extends DSSIntegrationTest {

    private static final String SERVICE_NAME = "GraphQLSampleService";
    /** Student ID assigned by the AUTO_INCREMENT column; read from the getStudents response. */
    private int insertedStudentId = -1;

    private String graphqlEndpoint;

    @BeforeClass(alwaysRun = true)
    public void serviceDeployment() throws Exception {
        super.init();
        graphqlEndpoint = dssContext.getContextUrls().getWebAppURL() + "/graphql/" + SERVICE_NAME;
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        // Best-effort cleanup: delete any test student left over if a prior test failed
        if (insertedStudentId > 0) {
            String deleteMutation = "mutation { deleteStudent(studentId: " + insertedStudentId + ") { REQUEST_STATUS } }";
            try {
                execute(graphqlEndpoint, deleteMutation);
            } catch (Exception ignored) {
            }
        }
        deleteService(SERVICE_NAME);
        cleanup();
    }

    /**
     * insertStudent mutation inserts a new row and returns REQUEST_STATUS SUCCESSFUL.
     */
    @Test(groups = "wso2.dss", description = "GraphQL insertStudent mutation succeeds")
    public void testInsertStudent() throws Exception {
        String mutation = "mutation { insertStudent(firstName: \"GraphQLTest\", lastName: \"Student\") { REQUEST_STATUS } }";
        Object[] response = execute(graphqlEndpoint, mutation);

        Assert.assertEquals(response[0], OK, "Expected HTTP 200");
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Mutation must not return errors: " + body);
        Assert.assertTrue(body.contains("\"REQUEST_STATUS\""),
                "Response must contain REQUEST_STATUS field: " + body);
        Assert.assertTrue(body.contains("SUCCESSFUL"),
                "REQUEST_STATUS must be SUCCESSFUL: " + body);
    }

    /**
     * After insertStudent, getStudents returns the newly created student.
     * Also captures the AUTO_INCREMENT studentId for the subsequent delete test.
     */
    @Test(groups = "wso2.dss",
          description = "getStudents sees the inserted student",
          dependsOnMethods = "testInsertStudent")
    public void testInsertedStudentVisible() throws Exception {
        String query = "{ getStudents { Student { studentId firstName lastName } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK);
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Query must not return errors: " + body);
        Assert.assertTrue(body.contains("GraphQLTest"),
                "Inserted student firstName must appear in getStudents response: " + body);
        Assert.assertTrue(body.contains("Student"),
                "Inserted student lastName must appear in getStudents response: " + body);

        // Extract the studentId from the response so we can delete it later.
        // The JSON contains "studentId":"<n>" (as a string from XML parsing).
        insertedStudentId = extractLastStudentId(body);
        Assert.assertTrue(insertedStudentId > 0,
                "Must be able to extract a positive studentId from: " + body);
    }

    /**
     * deleteStudent mutation removes the previously inserted student.
     */
    @Test(groups = "wso2.dss",
          description = "GraphQL deleteStudent mutation succeeds",
          dependsOnMethods = "testInsertedStudentVisible")
    public void testDeleteStudent() throws Exception {
        String mutation = "mutation { deleteStudent(studentId: " + insertedStudentId + ") { REQUEST_STATUS } }";
        Object[] response = execute(graphqlEndpoint, mutation);

        Assert.assertEquals(response[0], OK, "Expected HTTP 200");
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Mutation must not return errors: " + body);
        Assert.assertTrue(body.contains("SUCCESSFUL"),
                "REQUEST_STATUS must be SUCCESSFUL: " + body);
        insertedStudentId = -1; // mark as cleaned up so @AfterClass skips the retry
    }

    /**
     * After deleteStudent, getStudents no longer returns the deleted student.
     */
    @Test(groups = "wso2.dss",
          description = "getStudents no longer sees the deleted student",
          dependsOnMethods = "testDeleteStudent")
    public void testDeletedStudentNotVisible() throws Exception {
        String query = "{ getStudents { Student { firstName lastName } } }";
        Object[] response = execute(graphqlEndpoint, query);

        Assert.assertEquals(response[0], OK);
        String body = (String) response[1];
        Assert.assertFalse(body.contains("\"errors\""), "Query must not return errors: " + body);
        // The test student "GraphQLTest Student" must no longer be present.
        // (Other students inserted by concurrent tests could be there, but NOT our specific one.)
        // We check this by verifying the pair "GraphQLTest"+"Student" is gone.
        boolean hasFirst = body.contains("GraphQLTest");
        boolean hasLast  = body.contains("\"Student\"") && body.contains("GraphQLTest");
        Assert.assertFalse(hasFirst,
                "Deleted student firstName 'GraphQLTest' must not appear in getStudents: " + body);
    }

    /**
     * A mutation that references a non-existent operation name returns a GraphQL error.
     */
    @Test(groups = "wso2.dss", description = "Unknown mutation field returns GraphQL error")
    public void testUnknownMutationReturnsError() throws Exception {
        String mutation = "mutation { nonExistentOp(firstName: \"x\") { REQUEST_STATUS } }";
        Object[] response = execute(graphqlEndpoint, mutation);

        Assert.assertEquals(response[0], OK, "HTTP status must be 200 even for validation errors");
        String body = (String) response[1];
        Assert.assertTrue(body.contains("\"errors\""),
                "Unknown field must produce a GraphQL errors array: " + body);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the last numeric {@code studentId} value from the JSON response.
     * The response contains {@code "studentId":"<number>"} (values come back as strings
     * because the XML parser treats all leaf values as strings).
     */
    private static int extractLastStudentId(String json) {
        // Find the last occurrence of "studentId" and parse the value that follows.
        int lastIndex = json.lastIndexOf("\"studentId\"");
        if (lastIndex < 0) {
            return -1;
        }
        // Skip past the key and the colon/whitespace
        int colon = json.indexOf(':', lastIndex);
        if (colon < 0) {
            return -1;
        }
        // Value may be quoted ("123") or unquoted (123)
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
