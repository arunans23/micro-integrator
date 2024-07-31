# WSO2 Micro Integrator 4.3.0 Performance Test Results

During each release, we execute various automated performance test scenarios and publish the results.

| Test Scenarios | Description |
| --- | --- |
| Direct Proxy | Passthrough proxy service |
| CBR Proxy | Routing the message based on the content of the message body |
| XSLT Proxy | Having XSLT transformations in request and response paths |
| Direct API | Passthrough API service |

Our test client is [Apache JMeter](https://jmeter.apache.org/index.html). We test each scenario for a fixed duration of
time. We split the test results into warmup and measurement parts and use the measurement part to compute the
performance metrics.

Test scenarios use a [Netty](https://netty.io/) based back-end service which echoes back any request
posted to it after a specified period of time.

We run the performance tests under different numbers of concurrent users, message sizes (payloads) and back-end service
delays.

The main performance metrics:

1. **Throughput**: The number of requests that the WSO2 Micro Integrator 4.3.0 processes during a specific time interval (e.g. per second).
2. **Response Time**: The end-to-end latency for an operation of invoking a service in WSO2 Micro Integrator 4.3.0 . The complete distribution of response times was recorded.

In addition to the above metrics, we measure the load average and several memory-related metrics.

The following are the test parameters.

| Test Parameter | Description | Values |
| --- | --- | --- |
| Scenario Name | The name of the test scenario. | Refer to the above table. |
| Heap Size | The amount of memory allocated to the application | 2G |
| Concurrent Users | The number of users accessing the application at the same time. | 100, 200, 500, 1000 |
| Message Size (Bytes) | The request payload size in Bytes. | 500, 1000, 10000, 100000 |
| Back-end Delay (ms) | The delay added by the Back-end service. | 0 |

The duration of each test is **360 seconds**. The warm-up period is **180 seconds**.
The measurement results are collected after the warm-up period.

The performance tests were executed on 1 AWS CloudFormation stack.


System information for WSO2 Micro Integrator 4.3.0 in 1st AWS CloudFormation stack.

| Class | Subclass | Description | Value |
| --- | --- | --- | --- |
| AWS | EC2 | AMI-ID | ami-055744c75048d8296 |
| AWS | EC2 | Instance Type | c5.large |
| System | Processor | CPU(s) | 2 |
| System | Processor | Thread(s) per core | 2 |
| System | Processor | Core(s) per socket | 1 |
| System | Processor | Socket(s) | 1 |
| System | Processor | Model name | Intel(R) Xeon(R) Platinum 8275CL CPU @ 3.00GHz |
| System | Memory | BIOS | 64 KiB |
| System | Memory | System Memory | 4 GiB |
| System | Storage | Block Device: nvme0n1 | 8G |
| Operating System | Distribution | Release | Ubuntu 18.04.6 LTS |
| Operating System | Distribution | Kernel | Linux ip-10-0-1-33 5.4.0-1103-aws #111~18.04.1-Ubuntu SMP Tue May 23 20:04:10 UTC 2023 x86_64 x86_64 x86_64 GNU/Linux |


The following are the measurements collected from each performance test conducted for a given combination of
test parameters.

| Measurement | Description |
| --- | --- |
| Error % | Percentage of requests with errors |
| Average Response Time (ms) | The average response time of a set of results |
| Standard Deviation of Response Time (ms) | The “Standard Deviation” of the response time. |
| 99th Percentile of Response Time (ms) | 99% of the requests took no more than this time. The remaining samples took at least as long as this |
| Throughput (Requests/sec) | The throughput measured in requests per second. |
| Average Memory Footprint After Full GC (M) | The average memory consumed by the application after a full garbage collection event. |

The following is the summary of performance test results collected for the measurement period.

|  Scenario Name | Heap Size | Concurrent Users | Message Size (Bytes) | Back-end Service Delay (ms) | Error % | Throughput (Requests/sec) | Average Response Time (ms) | Standard Deviation of Response Time (ms) | 99th Percentile of Response Time (ms) | WSO2 Micro Integrator 4.3.0 GC Throughput (%) | Average WSO2 Micro Integrator 4.3.0 Memory Footprint After Full GC (M) |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|  Direct API | 2G | 100 | 500 | 0 | 0 | 8257.89 | 12.05 | 11.06 | 61 | N/A | N/A |
|  Direct API | 2G | 100 | 1000 | 0 | 0 | 8339.04 | 11.93 | 11.05 | 60 | N/A | N/A |
|  Direct API | 2G | 100 | 10000 | 0 | 0 | 6096.78 | 16.33 | 13.51 | 74 | N/A | N/A |
|  Direct API | 2G | 100 | 100000 | 0 | 0 | 1841.53 | 54.19 | 8.87 | 78 | N/A | N/A |
|  Direct API | 2G | 200 | 500 | 0 | 0 | 8466.97 | 23.55 | 16.84 | 90 | N/A | N/A |
|  Direct API | 2G | 200 | 1000 | 0 | 0 | 8432.03 | 23.65 | 16.6 | 89 | 99.47 |  |
|  Direct API | 2G | 200 | 10000 | 0 | 0 | 5959.8 | 33.48 | 20.25 | 106 | 99.64 |  |
|  Direct API | 2G | 200 | 100000 | 0 | 0 | 1688.32 | 118.28 | 18.71 | 169 | 99.75 |  |
|  Direct API | 2G | 500 | 500 | 0 | 0 | 8110.75 | 61.53 | 31.47 | 165 | 99.65 |  |
|  Direct API | 2G | 500 | 1000 | 0 | 0 | 8138.61 | 61.3 | 31.84 | 169 | 99.59 |  |
|  Direct API | 2G | 500 | 10000 | 0 | 0 | 6089.91 | 81.95 | 39.14 | 204 | 99.59 |  |
|  Direct API | 2G | 500 | 100000 | 0 | 0 | 1547.31 | 323.17 | 46.98 | 445 | 99.63 |  |
|  Direct API | 2G | 1000 | 500 | 0 | 0 | 8050.54 | 123.7 | 51.89 | 279 | 99.55 |  |
|  Direct API | 2G | 1000 | 1000 | 0 | 0 | 7819.1 | 127.26 | 53.01 | 287 | 99.49 |  |
|  Direct API | 2G | 1000 | 10000 | 0 | 0 | 5988.24 | 166.61 | 66.81 | 351 | 99.47 |  |
|  Direct API | 2G | 1000 | 100000 | 0 | 0 | 1468.15 | 678.79 | 93.6 | 899 | 99.5 |  |
|  CBR Proxy | 2G | 100 | 500 | 0 | 0 | 4912.94 | 20.28 | 13.7 | 74 |  |  |
|  CBR Proxy | 2G | 100 | 1000 | 0 | 0 | 4201.39 | 23.73 | 15.75 | 86 | N/A | N/A |
|  CBR Proxy | 2G | 100 | 10000 | 0 | 0 | 1077.66 | 92.66 | 57.45 | 281 | N/A | N/A |
|  CBR Proxy | 2G | 100 | 100000 | 0 | 0 | 75.2 | 1326.33 | 351.6 | 2383 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 500 | 0 | 95.62 | 23699.93 | 7.53 | 14.26 | 53 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 1000 | 0 | 0 | 4263.5 | 46.82 | 26.83 | 140 |  |  |
|  CBR Proxy | 2G | 200 | 10000 | 0 | 0 | 1089.77 | 183.41 | 95.22 | 471 | N/A | N/A |
|  CBR Proxy | 2G | 200 | 100000 | 0 | 0 | 62.71 | 3161.34 | 899.65 | 5183 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 500 | 0 | 0 | 5085.64 | 97.86 | 76.95 | 250 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 1000 | 0 | 0 | 4341.52 | 114.99 | 53.85 | 285 |  |  |
|  CBR Proxy | 2G | 500 | 10000 | 0 | 0 | 1103.79 | 452.61 | 192.43 | 995 | N/A | N/A |
|  CBR Proxy | 2G | 500 | 100000 | 0 | 0 | 50.76 | 9549.67 | 2502.01 | 15039 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 500 | 0 | 3.12 | 140.03 | 5433.62 | 20717.66 | 120319 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 1000 | 0 | 0 | 4229.97 | 236.11 | 96.17 | 527 |  |  |
|  CBR Proxy | 2G | 1000 | 10000 | 0 | 0 | 692.8 | 1434.1 | 548.22 | 2975 | N/A | N/A |
|  CBR Proxy | 2G | 1000 | 100000 | 0 | 100 | 23720.92 | 36.88 | 32.9 | 152 | N/A | N/A |
|  Direct Proxy | 2G | 100 | 500 | 0 | 0 | 7947.11 | 12.52 | 10.83 | 58 |  |  |
|  Direct Proxy | 2G | 100 | 1000 | 0 | 0 | 7947.07 | 12.52 | 10.8 | 58 | N/A | N/A |
|  Direct Proxy | 2G | 100 | 10000 | 0 | 0 | 5599.17 | 17.78 | 11.58 | 59 | N/A | N/A |
|  Direct Proxy | 2G | 100 | 100000 | 0 | 0 | 1750.47 | 57 | 14.49 | 110 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 500 | 0 | 0 | 7994.25 | 24.93 | 17.03 | 88 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 1000 | 0 | 0 | 7859.56 | 25.37 | 17.53 | 90 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 10000 | 0 | 0 | 5484.91 | 36.37 | 19.8 | 100 | N/A | N/A |
|  Direct Proxy | 2G | 200 | 100000 | 0 | 0 | 1604.5 | 124.49 | 29.25 | 232 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 500 | 0 | 0 | 7514.51 | 66.4 | 33.18 | 174 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 1000 | 0 | 0 | 7460.89 | 66.89 | 32.17 | 171 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 10000 | 0 | 0 | 5671.51 | 87.99 | 40.44 | 209 | N/A | N/A |
|  Direct Proxy | 2G | 500 | 100000 | 0 | 0 | 1476.11 | 338.64 | 72.01 | 575 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 500 | 0 | 0 | 7440.23 | 133.76 | 56.84 | 305 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 1000 | 0 | 0 | 7450.32 | 133.65 | 55.84 | 295 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 10000 | 0 | 0 | 5728.81 | 173.81 | 68.48 | 371 | N/A | N/A |
|  Direct Proxy | 2G | 1000 | 100000 | 0 | 98.8 | 8367.67 | 86.43 | 471.54 | 2959 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 500 | 0 | 0 | 2125.86 | 46.34 | 33.53 | 170 |  |  |
|  XSLT Proxy | 2G | 100 | 1000 | 0 | 0 | 1716.62 | 58.15 | 39.24 | 194 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 10000 | 0 | 0 | 285.3 | 350.16 | 189.69 | 891 | N/A | N/A |
|  XSLT Proxy | 2G | 100 | 100000 | 0 | 0 | 21.26 | 4623.08 | 656.89 | 6527 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 500 | 0 | 0 | 2030.02 | 98.37 | 281.68 | 359 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 1000 | 0 | 0 | 65.09 | 2420.21 | 6979.67 | 7967 | N/A | N/A |
|  XSLT Proxy | 2G | 200 | 10000 | 0 | 0 | 266.81 | 747.32 | 323.77 | 1663 |  |  |
|  XSLT Proxy | 2G | 200 | 100000 | 0 | 0 | 19.36 | 9874.46 | 1267.38 | 12863 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 500 | 0 | 0 | 2363.36 | 211.36 | 208.38 | 523 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 1000 | 0 | 0 | 1712.96 | 291.7 | 132.33 | 687 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 10000 | 0 | 6.44 | 27.9 | 13685.89 | 27882.41 | 120319 | N/A | N/A |
|  XSLT Proxy | 2G | 500 | 100000 | 0 | 0 | 16.45 | 27513.33 | 4454.47 | 38655 |  |  |
|  XSLT Proxy | 2G | 1000 | 500 | 0 | 100 | 25339.9 | 33.89 | 29.95 | 137 | N/A | N/A |
|  XSLT Proxy | 2G | 1000 | 1000 | 0 | 0 | 1075.17 | 925.77 | 375.43 | 2175 |  |  |
|  XSLT Proxy | 2G | 1000 | 10000 | 0 | 0 | 208.53 | 4690.84 | 1411.73 | 8639 | N/A | N/A |
|  XSLT Proxy | 2G | 1000 | 100000 | 0 | 36.68 | 7.54 | 82937.77 | 35707.82 | 212991 | N/A | N/A |
