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

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import org.apache.axiom.om.OMOutputFormat;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.micro.integrator.dataservices.core.DataServiceFault;
import org.wso2.micro.integrator.dataservices.core.description.query.Query;
import org.wso2.micro.integrator.dataservices.core.engine.DSOMDataSource;
import org.wso2.micro.integrator.dataservices.core.engine.DataService;
import org.wso2.micro.integrator.dataservices.core.engine.OutputElementGroup;
import org.wso2.micro.integrator.dataservices.core.engine.ParamValue;
import org.wso2.micro.integrator.dataservices.core.engine.QueryParam;
import org.wso2.micro.integrator.dataservices.core.engine.Result;
import org.wso2.micro.integrator.dataservices.core.engine.StaticOutputElement;
import org.wso2.micro.integrator.dataservices.core.description.operation.Operation;

import javax.xml.namespace.QName;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a GraphQL schema from a DataService definition.
 * Operations with results become Query fields; operations without results become Mutation fields.
 */
public class GraphQLSchemaBuilder {

    private static final Log log = LogFactory.getLog(GraphQLSchemaBuilder.class);

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String REQUEST_STATUS_TYPE = "RequestStatus";

    /**
     * Builds a {@link GraphQL} instance wired to execute against the given DataService.
     */
    public static GraphQL build(DataService dataService) {
        // Collect schema SDL and runtime wiring
        StringBuilder sdl = new StringBuilder();
        RuntimeWiring.Builder runtimeWiring = RuntimeWiring.newRuntimeWiring();

        List<String> queryFields = new ArrayList<>();
        List<String> mutationFields = new ArrayList<>();
        Map<String, String> objectTypes = new HashMap<>();  // typeName -> SDL fragment
        TypeRuntimeWiring.Builder queryWiring = TypeRuntimeWiring.newTypeWiring("Query");
        TypeRuntimeWiring.Builder mutationWiring = TypeRuntimeWiring.newTypeWiring("Mutation");

        Set<String> knownBoxcarringOps = new HashSet<>();
        knownBoxcarringOps.add("begin_boxcar");
        knownBoxcarringOps.add("end_boxcar");
        knownBoxcarringOps.add("abort_boxcar");

        for (Map.Entry<String, Operation> entry : dataService.getOperations().entrySet()) {
            String opName = entry.getKey();
            Operation op = entry.getValue();

            // Skip batch/boxcarring operations
            if (opName.endsWith("_batch_req") || knownBoxcarringOps.contains(opName)) {
                continue;
            }

            // Sanitize operation name for GraphQL (must be a valid identifier)
            String fieldName = sanitizeName(opName);
            if (fieldName == null || fieldName.isEmpty()) {
                continue;
            }

            Query query = op.getCallQuery().getQuery();
            boolean hasResult = query.hasResult();

            // Build argument string
            List<String> argDefs = new ArrayList<>();
            for (QueryParam qp : query.getQueryParams()) {
                if ("OUT".equals(qp.getType())) continue;
                String argName = sanitizeName(qp.getName());
                if (argName == null) continue;
                String argType = xsdToGraphQLType(qp.getSqlType());
                argDefs.add(argName + ": " + argType);
            }
            String argStr = argDefs.isEmpty() ? "" : "(" + String.join(", ", argDefs) + ")";

            if (hasResult) {
                // Build return type from result elements
                Result result = query.getResult();
                String returnTypeName = buildResultType(opName, result, objectTypes);
                queryFields.add("  " + fieldName + argStr + ": " + returnTypeName);
                queryWiring.dataFetcher(fieldName, buildDataFetcher(dataService, opName, query));
            } else {
                // No result → Mutation returning a status string
                ensureRequestStatusType(objectTypes);
                mutationFields.add("  " + fieldName + argStr + ": " + REQUEST_STATUS_TYPE);
                mutationWiring.dataFetcher(fieldName, buildMutationDataFetcher(dataService, opName));
            }
        }

        // Assemble SDL
        // Object types
        for (Map.Entry<String, String> typeEntry : objectTypes.entrySet()) {
            sdl.append(typeEntry.getValue()).append("\n");
        }

        // Query type
        if (!queryFields.isEmpty()) {
            sdl.append("type Query {\n");
            for (String f : queryFields) sdl.append(f).append("\n");
            sdl.append("}\n");
        } else {
            // GraphQL requires at least one field in Query
            sdl.append("type Query {\n  _empty: String\n}\n");
        }

        // Mutation type
        if (!mutationFields.isEmpty()) {
            sdl.append("type Mutation {\n");
            for (String f : mutationFields) sdl.append(f).append("\n");
            sdl.append("}\n");
        }

        if (!queryFields.isEmpty()) {
            runtimeWiring.type(queryWiring.build());
        }
        if (!mutationFields.isEmpty()) {
            runtimeWiring.type(mutationWiring.build());
        }

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl.toString());
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring.build());

        return GraphQL.newGraphQL(schema).build();
    }

    /**
     * Returns the SDL for the result object type and records it in {@code objectTypes}.
     */
    private static String buildResultType(String opName, Result result,
                                          Map<String, String> objectTypes) {
        OutputElementGroup elementGroup = result.getDefaultElementGroup();
        if (elementGroup == null) {
            return "String";
        }

        // Inner row type
        String rowTypeName = null;
        String rowName = result.getRowName();
        if (rowName != null && !rowName.isEmpty()) {
            rowTypeName = capitalize(sanitizeName(opName)) + "Row";
            if (!objectTypes.containsKey(rowTypeName)) {
                StringBuilder rowType = new StringBuilder("type " + rowTypeName + " {\n");
                for (StaticOutputElement element : elementGroup.getElementEntries()) {
                    String fieldName = sanitizeName(element.getName());
                    if (fieldName == null) continue;
                    String fieldType = xsdTypeQNameToGraphQL(element.getXsdType());
                    rowType.append("  ").append(fieldName).append(": ").append(fieldType).append("\n");
                }
                // Add attributes as fields too
                for (StaticOutputElement attr : elementGroup.getAttributeEntries()) {
                    String fieldName = sanitizeName(attr.getName());
                    if (fieldName == null) continue;
                    String fieldType = xsdTypeQNameToGraphQL(attr.getXsdType());
                    rowType.append("  ").append(fieldName).append(": ").append(fieldType).append("\n");
                }
                rowType.append("}");
                objectTypes.put(rowTypeName, rowType.toString());
            }
        }

        // Wrapper type
        String elementName = result.getElementName();
        String wrapperTypeName = capitalize(sanitizeName(opName)) + "Result";
        if (!objectTypes.containsKey(wrapperTypeName)) {
            StringBuilder wrapperType = new StringBuilder("type " + wrapperTypeName + " {\n");
            if (rowTypeName != null) {
                String listFieldName = rowName != null ? sanitizeName(rowName) : "items";
                if (listFieldName == null) listFieldName = "items";
                wrapperType.append("  ").append(listFieldName).append(": [").append(rowTypeName).append("]\n");
            } else {
                // Flat result - fields directly in wrapper
                for (StaticOutputElement element : elementGroup.getElementEntries()) {
                    String fieldName = sanitizeName(element.getName());
                    if (fieldName == null) continue;
                    String fieldType = xsdTypeQNameToGraphQL(element.getXsdType());
                    wrapperType.append("  ").append(fieldName).append(": ").append(fieldType).append("\n");
                }
            }
            wrapperType.append("}");
            objectTypes.put(wrapperTypeName, wrapperType.toString());
        }

        return wrapperTypeName;
    }

    private static void ensureRequestStatusType(Map<String, String> objectTypes) {
        if (!objectTypes.containsKey(REQUEST_STATUS_TYPE)) {
            objectTypes.put(REQUEST_STATUS_TYPE,
                    "type " + REQUEST_STATUS_TYPE + " {\n  REQUEST_STATUS: String\n}");
        }
    }

    /**
     * Builds a DataFetcher for a query-type (SELECT) operation.
     * Executes the DataService operation and parses the XML result into a Map.
     */
    private static DataFetcher<Object> buildDataFetcher(DataService dataService,
                                                         String opName, Query query) {
        return env -> {
            Map<String, ParamValue> params = new HashMap<>();
            for (QueryParam qp : query.getQueryParams()) {
                if ("OUT".equals(qp.getType())) continue;
                String argName = sanitizeName(qp.getName());
                Object argValue = env.getArgument(argName);
                if (argValue != null) {
                    params.put(qp.getName(), new ParamValue(String.valueOf(argValue)));
                }
            }
            try {
                return executeQuery(dataService, opName, params);
            } catch (Exception e) {
                log.error("Error executing GraphQL query: " + opName, e);
                throw new RuntimeException("Error executing query: " + opName + ": " + e.getMessage(), e);
            }
        };
    }

    /**
     * Builds a DataFetcher for a mutation-type (INSERT/UPDATE/DELETE) operation.
     */
    private static DataFetcher<Object> buildMutationDataFetcher(DataService dataService,
                                                                  String opName) {
        return env -> {
            Operation op = dataService.getOperations().get(opName);
            Map<String, ParamValue> params = new HashMap<>();
            if (op != null) {
                for (QueryParam qp : op.getCallQuery().getQuery().getQueryParams()) {
                    if ("OUT".equals(qp.getType())) continue;
                    String argName = sanitizeName(qp.getName());
                    Object argValue = env.getArgument(argName);
                    if (argValue != null) {
                        params.put(qp.getName(), new ParamValue(String.valueOf(argValue)));
                    }
                }
            }
            try {
                executeMutation(dataService, opName, params);
                Map<String, Object> status = new HashMap<>();
                status.put("REQUEST_STATUS", "SUCCESSFUL");
                return status;
            } catch (Exception e) {
                log.error("Error executing GraphQL mutation: " + opName, e);
                Map<String, Object> status = new HashMap<>();
                status.put("REQUEST_STATUS", "FAILED: " + e.getMessage());
                return status;
            }
        };
    }

    /**
     * Executes a DataService operation that returns a result set
     * and converts the XML output to a Map/List structure for GraphQL.
     */
    static Object executeQuery(DataService dataService, String opName,
                               Map<String, ParamValue> params) throws Exception {
        Query.resetQueryPreprocessing();
        Query.setQueryPreprocessingInitial(true);
        Query.setQueryPreprocessingSecondary(false);

        DSOMDataSource ds = new DSOMDataSource(dataService, opName, params);
        // First pass: pre-query (fetches data)
        ds.execute(null);

        // Second pass: serialize to XML
        Query.setQueryPreprocessingInitial(false);
        Query.setQueryPreprocessingSecondary(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ds.serialize(baos, new OMOutputFormat());
        String xml = baos.toString(StandardCharsets.UTF_8.name());

        return GraphQLXmlParser.parseXmlToMap(xml);
    }

    /**
     * Executes a DataService operation that does not return a result (INSERT/UPDATE/DELETE).
     */
    static void executeMutation(DataService dataService, String opName,
                                Map<String, ParamValue> params) throws DataServiceFault {
        Query.resetQueryPreprocessing();
        Query.setQueryPreprocessingInitial(true);
        Query.setQueryPreprocessingSecondary(false);

        DSOMDataSource ds = new DSOMDataSource(dataService, opName, params);
        try {
            ds.executeInOnly();
        } catch (Exception e) {
            throw new DataServiceFault(e, "Error executing mutation: " + opName);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Sanitizes a name to be a valid GraphQL identifier.
     * Replaces non-alphanumeric characters with underscores.
     */
    static String sanitizeName(String name) {
        if (name == null || name.isEmpty()) return null;
        // Replace hyphens and dots with underscores, remove other invalid chars
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        // Must not start with a digit
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Maps a SQL type string to a GraphQL scalar type name.
     */
    static String xsdToGraphQLType(String sqlType) {
        if (sqlType == null) return "String";
        switch (sqlType.toUpperCase()) {
            case "INTEGER":
            case "INT":
            case "LONG":
            case "TINYINT":
            case "SMALLINT":
            case "BIGINT":
                return "Int";
            case "DOUBLE":
            case "FLOAT":
            case "DECIMAL":
            case "NUMERIC":
            case "REAL":
                return "Float";
            case "BOOLEAN":
            case "BIT":
                return "Boolean";
            default:
                return "String";
        }
    }

    /**
     * Maps an XSD QName type to a GraphQL scalar type name.
     */
    static String xsdTypeQNameToGraphQL(QName xsdType) {
        if (xsdType == null) return "String";
        String localPart = xsdType.getLocalPart();
        if (localPart == null) return "String";
        switch (localPart.toLowerCase()) {
            case "integer":
            case "int":
            case "long":
            case "short":
            case "byte":
            case "nonnegativeinteger":
            case "positiveinteger":
            case "unsignedlong":
            case "unsignedint":
            case "unsignedshort":
            case "unsignedbyte":
                return "Int";
            case "double":
            case "float":
            case "decimal":
                return "Float";
            case "boolean":
                return "Boolean";
            default:
                return "String";
        }
    }
}
