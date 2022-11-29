# Analyzing ALB Access Logs with Amazon Athena
This document includes an overview of setting up Amazon Athena and table creation for Elastic Load Balancing log analysis.

> For a CDK & CloudFormation sample that deploys this solution: https://github.com/aws/elastic-load-balancing-tools/blob/master/log-analysis-elb

Setup steps:
1. Setup AWS account, ELB + enable access logs
2. Configure your S3 bucket for Athena access
3. Setup your new database and table refer to https://docs.aws.amazon.com/athena/latest/ug/elasticloadbalancer-classic-logs.html#create-elb-table for the latest query to create the table
```sql
CREATE EXTERNAL TABLE IF NOT EXISTS clb_logs (
 request_timestamp string,
 elb_name string,
 request_ip string,
 request_port int,
 backend_ip string,
 backend_port int,
 request_processing_time double,
 backend_processing_time double,
 response_processing_time double,
 elb_response_code string,
 backend_response_code string,
 received_bytes bigint,
 sent_bytes bigint,
 request_verb string,
 url string,
 protocol string,
 user_agent string,
 ssl_cipher string,
 ssl_protocol string
)
ROW FORMAT SERDE 'org.apache.hadoop.hive.serde2.RegexSerDe'
WITH SERDEPROPERTIES (
 'serialization.format' = '1',
 'input.regex' = '([^ ]*) ([^ ]*) ([^ ]*):([0-9]*) ([^ ]*)[:\-]([0-9]*) ([-.0-9]*) ([-.0-9]*) ([-.0-9]*) (|[-0-9]*) (-|[-0-9]*) ([-0-9]*) ([-0-9]*) \\\"([^ ]*) ([^ ]*) (- |[^ ]*)\\\" (\"[^\"]*\") ([A-Z0-9-]+) ([A-Za-z0-9.-]*)$' )
LOCATION 's3://your_log_bucket/prefix/AWSLogs/AWS_account_ID/elasticloadbalancing/';
```
4. Run queries
### Select data information for time period
```sql
    SELECT client_ip,
         count(client_ip) AS requestCount,
         avg(received_bytes+sent_bytes) AS avgTransactionSize,
         sum(received_bytes+sent_bytes) AS totalBytes
    FROM clb_logs
    WHERE time > '2018-11-01'
            AND time < '2018-11-29'
    GROUP BY  client_ip
    ORDER BY  requestCount DESC limit 10;
```
### View total data processed for time period
```sql
    SELECT count(client_ip) AS totalRequestCount,
         avg(received_bytes+sent_bytes) AS avgTransactionSize,
         sum(received_bytes+sent_bytes) AS totalBytes
    FROM clb_logs
    WHERE time > '2018-11-29'
            AND time < '2018-12-01';
```
### Select request IPs
```sql
    SELECT DISTINCT client_ip
    FROM clb_logs limit 100;
```
### Get top 100 clients by request count
```sql
    SELECT client_ip,
         count(client_ip) AS ct
    FROM clb_logs
    GROUP BY  client_ip
    ORDER BY  ct DESC limit 100;
```
#### plus response+request size
```sql
    SELECT client_ip,
         count(client_ip) AS requestCount,
         avg(received_bytes+sent_bytes) AS avgTransactionSize
    FROM clb_logs
    GROUP BY  client_ip
    ORDER BY  requestCount DESC limit 100;
```
### Ciphers by use
```sql
    SELECT DISTINCT ssl_cipher,
         count(ssl_cipher) AS cipherCount
    FROM clb_logs
    GROUP BY  ssl_cipher limit 100;
```
### Top 100 backends
```sql
    SELECT target_ip AS backend,
         count(target_ip) AS count,
         avg(received_bytes)+avg(sent_bytes) AS avgTransactionSize
    FROM clb_logs
    GROUP BY  target_ip
    ORDER BY  count DESC limit 100;
```
### Summary of ELB
```sql
    SELECT count(1) AS requests,
         count(1)/date_diff('second',date_parse(min(time),'%Y-%m-%dT%H:%i:%s.%fZ'),date_parse(max(time),'%Y-%m-%dT%H:%i:%s.%fZ')) AS requestPerSecond,avg(received_bytes + sent_bytes) AS avg_requestSize_bytes, min(time) AS startTime, max(time) AS endTime
    FROM clb_logs;
```