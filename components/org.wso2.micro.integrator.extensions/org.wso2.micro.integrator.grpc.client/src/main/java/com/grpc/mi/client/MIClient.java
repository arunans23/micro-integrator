package com.grpc.mi.client;

import com.grpc.mi.service.*;
import com.grpc.mi.service.API;
import com.grpc.mi.service.APIList;
import com.grpc.mi.service.ServerInfo;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.TimeUnit;

public class MIClient {
    private MIServiceGrpc.MIServiceStub stub;
    StreamObserver<DataRequest> requestObserver;
    private final String nodeID = "dev_Node";
    private final String groupID = "dev_Grp";
    //Hard-coded
    API api = API.newBuilder()
            .setTracing("disabled")
            .setStats("disabled").setPort(-1)
            .setConfiguration("<api xmlns=\\\"http://ws.apache.org/ns/synapse\\\" name=\\\"HealthcareAPI\\\" context=\\\"/healthcare\\\" binds-to=\\\"default\\\"><resource methods=\\\"GET\\\" binds-to=\\\"default\\\" uri-template=\\\"/doctor/{doctorType}\\\"><inSequence><clone><target><sequence><call><endpoint key=\\\"GrandOakEndpoint\\\"/><\\/call><\\/sequence><\\/target><target><sequence><payloadFactory media-type=\\\"json\\\"><format>\\n\\t\\t\\t\\t\\t\\t\\t\\t{ \\\"doctorType\\\": \\\"$1\\\" }\\n\\t\\t\\t\\t\\t\\t\\t<\\/format><args><arg evaluator=\\\"xml\\\" expression=\\\"$ctx:uri.var.doctorType\\\"/><\\/args><\\/payloadFactory><call><endpoint key=\\\"PineValleyEndpoint\\\"/><\\/call><\\/sequence><\\/target><\\/clone><aggregate><completeCondition><messageCount min=\\\"-1\\\" max=\\\"-1\\\"/><\\/completeCondition><onComplete aggregateElementType=\\\"root\\\" expression=\\\"json-eval($.doctors.doctor)\\\"><respond/><\\/onComplete><\\/aggregate><\\/inSequence><outSequence/><faultSequence/><\\/resource><\\/api>")
            .setName("HealthcareAPI")
            .setContext("/healthcare")
            .addResources(Resource.newBuilder().addMethods("GET").setUrl("/doctor/{doctorType}").build())
            .setVersion("N/A")
            .setUrl("http://localhost:8290/healthcare").build();
    APISummary apiSummary = APISummary.newBuilder()
            .setTracing("disabled")
            .setName("HealthcareAPI")
            .setUrl("http://localhost:8290/healthcare").build();
    APIList apiList = APIList.newBuilder().setCount(1).addApiSummaries(apiSummary).build();

    Endpoint endpoint1 = Endpoint.newBuilder()
            .setTracing("disabled")
            .setMethod("GET")
            .setAdvanced("{\"suspendState\":{\"errorCodes\":[],\"maxDuration\":9223372036854775807,\"initialDuration\":-1}")
            .setTimeoutState("{\"errorCodes\":[],\"reties\":0}}")
            .setConfiguration("<endpoint xmlns=\\\"http://ws.apache.org/ns/synapse\\\" name=\\\"GrandOakEndpoint\\\"><http method=\\\"GET\\\" uri-template=\\\"http://localhost:9090/grandOak/doctors/{uri.var.doctorType}\\\"/><\\/endpoint>")
            .setUriTemplate("http://localhost:9090/grandOak/doctors/")
            .setName("GrandOakEndpoint")
            .setType("HTTP Endpoint")
            .setIsActive(true).build();
    Endpoint endpoint2 = Endpoint.newBuilder()
            .setTracing("disabled")
            .setMethod("POST")
            .setAdvanced("{\"suspendState\":{\"errorCodes\":[],\"maxDuration\":9223372036854775807,\"initialDuration\":-1}")
            .setTimeoutState("{\"errorCodes\":[],\"reties\":0}}")
            .setConfiguration("<endpoint xmlns=\\\"http://ws.apache.org/ns/synapse\\\" name=\\\"PineValleyEndpoint\\\"><http method=\\\"POST\\\" uri-template=\\\"http://localhost:9091/pineValley/doctors\\\"/><\\/endpoint>")
            .setUriTemplate("http://localhost:9091/pineValley/doctors")
            .setName("PineValleyEndpoint")
            .setType("HTTP Endpoint")
            .setIsActive(true).build();
    EndpointSummary endpointSummary1 = EndpointSummary.newBuilder()
            .setName("GrandOakEndpoint")
            .setType("HTTP Endpoint")
            .setIsActive(true).build();
    EndpointSummary endpointSummary2 = EndpointSummary.newBuilder()
            .setName("PineValleyEndpoint")
            .setType("HTTP Endpoint")
            .setIsActive(true).build();
    EndpointList endpointList = EndpointList.newBuilder()
            .setCount(2)
            .addEndPointSummaries(endpointSummary1)
            .addEndPointSummaries(endpointSummary2).build();
    ServerInfo serverInfo = ServerInfo.newBuilder()
            .setProductVersion("4.2.0-alpha")
            .setOsVersion("10.0")
            .setJavaVersion("11.0.18")
            .setCarbonHome("C:\\Users\\RAVINF~1\\WSO2\\Github\\Builds\\WSO2MI~1.0-S\\bin\\..")
            .setJavaVendor("OpenLogic")
            .setOsName("Windows 10")
            .setProductName("WSO2 Micro Integrator")
            .setJavaHome("C:\\Program Files\\OpenJDK\\jdk-11.0.18.10-hotspot").build();
    public MIClient(Channel channel){
        stub = MIServiceGrpc.newStub(channel);
    }
    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
        MIClient client = new MIClient(channel);
        client.dataExchange();
    }

    public void dataExchange() throws InterruptedException {
        StreamObserver<DataResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(DataResponse dataResponse) {
                int responseType = dataResponse.getResponseType().getNumber();
                String response = dataResponse.getResponse();
                if (responseType == 0){
                    requestObserver.onNext(DataRequest.newBuilder().setServerInfo(serverInfo).build());
                } else if (responseType == 1) {
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setApiList(apiList).build());
                    } else {
                        int count = 0;
                        APIList.Builder responseApiList = APIList.newBuilder();
                        for (APISummary apisummary : apiList.getApiSummariesList()){
                            if (apisummary.getName().toLowerCase().contains(response.toLowerCase())){
                                count++;
                                responseApiList.addApiSummaries(apisummary);
                            }
                        }
                        responseApiList.setCount(count);
                        requestObserver.onNext(DataRequest.newBuilder().setApiList(responseApiList.build()).build());
                    }
                } else if (responseType == 2) {
                    requestObserver.onNext(DataRequest.newBuilder().setApi(api).build());
                }  else if (responseType == 3) {
                    if (response.equals("")) {
                        requestObserver.onNext(DataRequest.newBuilder().setEndpointList(endpointList).build());
                    } else {
                        int count = 0;
                        EndpointList.Builder responseEndpointList = EndpointList.newBuilder();
                        for (EndpointSummary endpointsummary : endpointList.getEndPointSummariesList()){
                            if (endpointsummary.getName().toLowerCase().contains(response.toLowerCase())){
                                count++;
                                responseEndpointList.addEndPointSummaries(endpointsummary);
                            }
                        }
                        responseEndpointList.setCount(count);
                        requestObserver.onNext(DataRequest.newBuilder().setEndpointList(responseEndpointList.build()).build());
                    }
                } else if (responseType == 4) {
                    if(response.equals(endpoint1.getName())){
                        requestObserver.onNext(DataRequest.newBuilder().setEndpoint(endpoint1).build());
                    } else if(response.equals(endpoint2.getName())){
                        requestObserver.onNext(DataRequest.newBuilder().setEndpoint(endpoint2).build());
                    } else {
                        System.out.println("Wrong Endpoint\nResponse: " + response);
                    }
                } else {
                    System.out.println("Invalid Request from Dashboard");
                }
            }

            @Override
            public void onError(Throwable throwable) {
                throwable.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("Client completed!");
            }

        };


        requestObserver = stub.dataExchange(responseObserver);

        Handshake handshake = Handshake.newBuilder().setNodeID(nodeID).setGroupID(groupID).build();
        DataRequest request = DataRequest.newBuilder().setHandshake(handshake).build();
        requestObserver.onNext(request);

        while(true){
            TimeUnit.SECONDS.sleep(5);
        }
//        requestObserver.onCompleted();


    }
}
