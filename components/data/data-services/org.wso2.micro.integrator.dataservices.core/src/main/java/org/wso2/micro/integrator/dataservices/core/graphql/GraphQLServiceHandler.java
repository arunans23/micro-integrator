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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.micro.integrator.dataservices.core.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;

import java.util.Collections;
import java.util.Map;

/**
 * Handles GraphQL request execution for a single DataService.
 * Wraps a pre-built {@link GraphQL} instance and processes incoming GraphQL requests.
 */
public class GraphQLServiceHandler {

    private static final Log log = LogFactory.getLog(GraphQLServiceHandler.class);

    private final DataService dataService;
    private final GraphQL graphQL;

    public GraphQLServiceHandler(DataService dataService) {
        this.dataService = dataService;
        this.graphQL = GraphQLSchemaBuilder.build(dataService);
        if (log.isDebugEnabled()) {
            log.debug("GraphQL schema built for service: " + dataService.getName());
        }
    }

    /**
     * Executes a GraphQL request.
     *
     * @param query         the GraphQL query/mutation string
     * @param operationName optional operation name (for documents with multiple operations)
     * @param variables     optional variable map
     * @return the execution result (containing data and/or errors)
     */
    public ExecutionResult execute(String query, String operationName, Map<String, Object> variables) {
        ExecutionInput.Builder inputBuilder = ExecutionInput.newExecutionInput()
                .query(query);
        if (operationName != null && !operationName.isEmpty()) {
            inputBuilder.operationName(operationName);
        }
        if (variables != null && !variables.isEmpty()) {
            inputBuilder.variables(variables);
        }
        return graphQL.execute(inputBuilder.build());
    }

    public DataService getDataService() {
        return dataService;
    }
}
