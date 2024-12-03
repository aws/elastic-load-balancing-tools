#!/usr/bin/env python3
from constructs import Construct
from aws_cdk import Stack, CfnTag, Aws

from aws_cdk import (
    aws_s3 as s3,
    aws_glue_alpha as glue,
    aws_kms as kms,
    aws_iam as iam,
    aws_athena as athena
)


class AthenaStack(Stack):

    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        # Create Glue Databse
        self.name = f'elb_logs_{self.stack_name.lower()}'
        self.logs_db = glue.Database(self, f'db_{self.name}',
                                     database_name=f'db_{self.name}')

        # Create S3 Bucket for query results and Workgroup
        self.encryption_key = kms.Key(self,
                                      f'key.logs.athena.{self.stack_name}-',
                                      enable_key_rotation=True,
                                      description=f'Key for ELB Logs Athena - {self.stack_name}')

        self.elb_logs_bucket = s3.Bucket(self,
                                         f'elb.logs.athena.{self.stack_name}-',
                                         encryption=s3.BucketEncryption.KMS,
                                         encryption_key=self.encryption_key)

        self.encryption_key.add_to_resource_policy(iam.PolicyStatement(
            effect=iam.Effect.ALLOW,
            actions=['kms:Encrypt', 'kms:Decrypt'],
            principals=[iam.AccountPrincipal(Aws.ACCOUNT_ID)],
            resources=['*']
        ))

        self.work_group = self.__create_work_group(
            self.elb_logs_bucket.bucket_name)

    def __create_work_group(self, bucket_name):
        cfn_work_group = athena.CfnWorkGroup(
            self, f'wg_elb_logs_{self.stack_name.lower()}',
            name=f'wg_elb_logs_{self.stack_name.lower()}',
            description='Workgroup for ELB logs',
            recursive_delete_option=True,
            state='ENABLED',
            work_group_configuration=athena.CfnWorkGroup.WorkGroupConfigurationProperty(
                publish_cloud_watch_metrics_enabled=False,
                requester_pays_enabled=False,
                result_configuration=athena.CfnWorkGroup.ResultConfigurationProperty(
                    output_location=f's3://{bucket_name}/logs_query_results/'
                )
            ),
            tags=[CfnTag(
                key="wg_elb_logs",
                value="True"
            )],
        )
        return cfn_work_group

    def __create_named_query(
            self, query_name,
            query_description, query_string):

        named_query = athena.CfnNamedQuery(
            self, query_name,
            database=self.logs_db.database_name,
            query_string=query_string,
            description=query_description,
            name=query_name,
            work_group=self.work_group.name)

        named_query.node.add_dependency(self.work_group)
        return named_query

    def __create_prepared_statement(self, name, description, statement):
        # Statement name does not support '-' character
        ps = athena.CfnPreparedStatement(self, name,
                                         query_statement=statement,
                                         statement_name=name.replace('-', '_'),
                                         work_group=self.work_group.name,
                                         description=description)
        ps.node.add_dependency(self.work_group)
        return ps

    def athena_alb(self, name, bucket_name, **kwargs):
        bkt_acc_id = kwargs.get('bucket_account', self.account)
        bucket_logs = s3.Bucket.from_bucket_name(self, f'alb_logs_{name}', bucket_name)

        if 'bucket_prefix' in kwargs:
            bucket_prefix = kwargs['bucket_prefix']
            bucket_path = f'{bucket_prefix}/AWSLogs/{bkt_acc_id}/elasticloadbalancing/{self.region}'
            alb_table_id = f'{bucket_name}_{bucket_prefix}'
            alb_table_id = alb_table_id.replace('/', '_')
        else:
            bucket_path = f'AWSLogs/{bkt_acc_id}/elasticloadbalancing/{self.region}'
            alb_table_id = f'{bucket_name}'

        alb_table_id = alb_table_id.lower()
        alb_table_name = f'tb_alb_logs_{alb_table_id}'

        logs_table = glue.Table(
            self, alb_table_name,
            database=self.logs_db,
            table_name=alb_table_name,
            bucket=bucket_logs,
            s3_prefix=bucket_path,
            columns=[
                glue.Column(
                    name='type',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='time',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='elb',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='client_ip',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='client_port',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='target_ip',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='target_port',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='request_processing_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='target_processing_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='response_processing_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='elb_status_code',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='target_status_code',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='received_bytes',
                    type=glue.Schema.BIG_INT),
                glue.Column(
                    name='sent_bytes',
                    type=glue.Schema.BIG_INT),
                glue.Column(
                    name='request_verb',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='request_url',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='request_proto',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='user_agent',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='ssl_cipher',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='ssl_protocol',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='target_group_arn',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='trace_id',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='domain_name',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='chosen_cert_arn',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='matched_rule_priority',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='request_creation_time',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='actions_executed',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='redirect_url',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='lambda_error_reason',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='target_port_list',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='target_status_code_list',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='classification',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='classification_reason',
                    type=glue.Schema.STRING)
            ],

            partition_keys=[
                glue.Column(
                    name='year',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='month',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='day',
                    type=glue.Schema.INTEGER
                )],

            data_format=glue.DataFormat(
                input_format=glue.InputFormat('org.apache.hadoop.mapred.TextInputFormat'),
                output_format=glue.OutputFormat('org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'),
                serialization_library=glue.SerializationLibrary('org.apache.hadoop.hive.serde2.RegexSerDe')
            )
        )

        logs_table_cfn = logs_table.node.default_child
        logs_table_cfn.add_override('Properties.TableInput.StorageDescriptor.SerdeInfo.Parameters.serialization\.format', 1)
        input_regex = '([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*):([0-9]*) ([^ ]*)[:-]([0-9]*) ([-.0-9]*) ([-.0-9]*) ([-.0-9]*) (|[-0-9]*) (-|[-0-9]*) ([-0-9]*) ([-0-9]*) \"([^ ]*) ([^ ]*) (- |[^ ]*)\" \"([^\"]*)\" ([A-Z0-9-]+) ([A-Za-z0-9.-]*) ([^ ]*) \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" ([-.0-9]*) ([^ ]*) \"([^\"]*)\" \"([^\"]*)\" \"([^ ]*)\" \"([^s]+?)\" \"([^s]+)\" \"([^ ]*)\" \"([^ ]*)\"'
        logs_table_cfn.add_override('Properties.TableInput.StorageDescriptor.SerdeInfo.Parameters.input\.regex', input_regex)
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.digits', '2')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.range', '01,31')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.digits', '2')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.range', '01,12')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.digits', '4')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.range', '2017,2050')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.enabled', 'true')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.EXTERNAL', 'TRUE')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.storage\.location\.template', f's3://{bucket_name}/{bucket_path}/${{year}}/${{month}}/${{day}}')

        self.__create_named_query(
            f'ALB - TLS Version - 30 days - {alb_table_id}', 'ALB - TLS Version - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, ssl_protocol, ROUND((COUNT(ssl_protocol)* 100.0 / (SELECT COUNT(*) FROM "{alb_table_name}" WHERE ssl_protocol != '-'  AND from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT() AS requests
            FROM "{alb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT ssl_protocol = '-'
            GROUP BY elb, ssl_protocol, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'ALB - TLS Ciphersuites - 30 days - {alb_table_id}', 'ALB - TLS Ciphersuites - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, ssl_cipher, ROUND((COUNT(ssl_cipher)* 100.0 / (SELECT COUNT(*) FROM "{alb_table_name}" WHERE ssl_cipher != '-' AND from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT() AS requests
            FROM "{alb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT ssl_cipher = '-'
            GROUP BY elb, ssl_cipher, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'ALB - Request Type - 30 days - {alb_table_id}', 'ALB - Request Type - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, type, round((Count(type)* 100.0 / (Select Count(*) From "{alb_table_name}" WHERE from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT(type) AS requests
            FROM "{alb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
            GROUP BY  elb, type, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'ALB - TLS Version and Ciphersuites combined - 30 days - {alb_table_id}', 'ALB - TLS Version and Ciphersuites combined - 30 days',
            f"""SELECT DISTINCT elb, ssl_cipher, ssl_protocol,  count(ssl_cipher) AS requests
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND NOT ssl_protocol = '-'
            GROUP BY elb, ssl_cipher,ssl_protocol
            ORDER BY requests DESC""")

        self.__create_named_query(
            f'ALB - Top 10 TLS 1.0 talkers - 30 days - {alb_table_id}', 'ALB - Top 10 TLS 1.0 talkers - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) AS requests
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND ssl_protocol = 'TLSv1'
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'ALB - Top 100 Clients and User Agents for TLS 1.0 - 30 days - {alb_table_id}', 'ALB - Top 10 TLS 1.0 talkers - 30 days',
            f"""SELECT DISTINCT(elb, client_ip, ssl_protocol, user_agent), COUNT(*) AS requests
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND ssl_protocol = 'TLSv1'
            GROUP BY (elb, client_ip, ssl_protocol, user_agent)
            ORDER BY requests DESC
            LIMIT 100""")

        self.__create_prepared_statement(
            f'alb_tls_version_{alb_table_id}',
            'ALB TLS Version Distribution',
            f"""SELECT elb, ssl_protocol, COUNT() AS requests
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > date_add('day', ?, current_timestamp)
                AND NOT ssl_protocol = '-'
                AND elb = ?
            GROUP BY elb, ssl_protocol
            """)

        self.__create_named_query(
            f'ALB - Top 10 talkers - Requests - 30 days - {alb_table_id}', 'ALB - Top 10 talkers - Requests - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) AS requests
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'ALB - Top 10 talkers - Requests - Time Range - {alb_table_id}', 'ALB - Top 10 talkers - Requests - Time Range days',
            f"""SELECT elb, client_ip, COUNT(*) AS requests
            FROM "{alb_table_name}"
            WHERE time >= '2022-09-12T00:00:00.000000Z'
            AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'ALB - Top 10 talkers - Megabytes - 30 days - {alb_table_id}', 'ALB - Top 10 talkers - Megabytes - 30 days',
            f"""SELECT elb, client_ip, ROUND(sum(received_bytes/1000000.0),2) as client_data_received_megabytes
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP by elb, client_ip
            ORDER by client_data_received_megabytes DESC;""")

        self.__create_named_query(
            f'ALB - Avg Request/Response size - 30 days - {alb_table_id}', 'ALB - Avg Request/Response size - 30 days',
            f"""SELECT elb, ROUND((avg(sent_bytes)/1000.0 + avg(received_bytes)/1000.0),2) as avg_request_response_kilobytes
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb""")

        self.__create_named_query(
            f'ALB - Target Distribution - 30 days - {alb_table_id}', 'ALB - Target Distribution - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, target_ip, ROUND((Count(target_ip)* 100.0 / (Select Count(*) From "{alb_table_name}" WHERE from_iso8601_timestamp(time) > var.intrvl AND NOT target_ip = '')),2)
            as backend_traffic_percentage
            FROM "{alb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT target_ip = ''
            GROUP by elb, target_ip, var.intrvl
            ORDER By count() DESC;""")

        self.__create_named_query(
            f'ALB - LB 4xx and 5xx errors - 30 days - {alb_table_id}', 'ALB - LB 4xx and 5xx errors - 30 days',
            f"""SELECT * FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                -- WHERE time >= '2022-09-12T00:00:00.000000Z'
                -- AND time <= '2022-09-19T23:59:59.9999999Z'
                -- AND elb_status_code = 400
                AND elb_status_code BETWEEN 400 AND 599;""")

        self.__create_named_query(
            f'ALB - Target 4xx and 5xx errors - 30 days - {alb_table_id}', 'ALB - Target 4xx and 5xx errors - 30 days',
            f"""SELECT * FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                -- WHERE time >= '2022-09-12T00:00:00.000000Z'
                -- AND time <= '2022-09-19T23:59:59.9999999Z'
                -- AND target_status_code = 400
                AND target_status_code BETWEEN 400 AND 599;""")

        self.__create_named_query(
            f'ALB - Client IPs per URL hit - 30 days - {alb_table_id}', 'ALB - Client IPs per URL hit - 30 days',
            f"""SELECT client_ip, elb, request_url, count(*) as count FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP by client_ip, elb, request_url
            ORDER by count DESC;""")

        self.__create_named_query(
            f'ALB - Top 100 user-agents - 30 days - {alb_table_id}', 'ALB - Top 100 user-agents - 30 days',
            f"""SELECT elb, user_agent, COUNT(*) AS requests
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb, user_agent
            ORDER BY requests DESC
            LIMIT 100;""")

        self.__create_named_query(
            f'ALB - Slow Responses - 30 days - {alb_table_id}', 'ALB - Slow Responses - 30 days',
            f"""SELECT * FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            AND target_processing_time >= 5.0""")

        self.__create_named_query(
            f'ALB - Aggregated Log Information - 30 days - {alb_table_id}', 'ALB - Aggregated Log Information - 30 days',
            f"""SELECT elb,
                count(1) AS requestCount,
                min(time) as firstLogTime,
                max(time) as lastLogTime,
                ROUND(avg(received_bytes+sent_bytes)) AS avgTransactionSize,
                sum(received_bytes) as totalBytesReceived,
                sum(sent_bytes) as totalBytesSent,
                sum(received_bytes+sent_bytes) AS totalBytes
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb
            ORDER BY requestCount DESC limit 10;""")

        self.__create_named_query(
            f'ALB - Processed Traffic by ELB & Target IP - 30 days - {alb_table_id}', 'ALB - Processed Traffic by ELB & Target IP - 30 days',
            f"""SELECT target_ip AS target,
                count(target_ip) AS count,
                sum(received_bytes) as totalRecvBytes,
                sum(sent_bytes) as totalSentBytes,
                avg(received_bytes)+avg(sent_bytes) AS avgTransactionSize,
                avg(request_processing_time) as avgRequestTime,
                avg(target_processing_time) as avgLatency,
                avg(response_processing_time) as avgResponseTime,
                regexp_extract("$path",'((1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])\.){{3}}(1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])') AS elb_ip,
                approx_percentile(request_processing_time, 0.99) as P99Request,
                approx_percentile(target_processing_time, 0.99) as P99Latency,
                approx_percentile(response_processing_time, 0.99) as P99Response,
                approx_percentile(request_processing_time, 0.999) as P999Request,
                approx_percentile(target_processing_time, 0.999) as P999Latency,
                approx_percentile(response_processing_time, 0.999) as P999Response
            FROM "{alb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY  target_ip, regexp_extract("$path",'((1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])\.){{3}}(1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])')
            ORDER BY count DESC limit 1000;
            """)

    def athena_clb(self, name, bucket_name, **kwargs):
        bkt_acc_id = kwargs.get('bucket_account', self.account)
        bucket_logs = s3.Bucket.from_bucket_name(self, f'clb_logs_{name}', bucket_name)

        if 'bucket_prefix' in kwargs:
            bucket_prefix = kwargs['bucket_prefix']
            bucket_path = f'{bucket_prefix}/AWSLogs/{bkt_acc_id}/elasticloadbalancing/{self.region}'
            clb_table_id = f'{bucket_name}_{bucket_prefix}'
            clb_table_id = clb_table_id.replace('/', '_')
        else:
            bucket_path = f'AWSLogs/{bkt_acc_id}/elasticloadbalancing/{self.region}'
            clb_table_id = bucket_name

        clb_table_id = clb_table_id.lower()
        clb_table_name = f'tb_clb_logs_{clb_table_id}'

        logs_table = glue.Table(
            self, clb_table_name,
            database=self.logs_db,
            table_name=clb_table_name,
            bucket=bucket_logs,
            s3_prefix=bucket_path,
            columns=[
                glue.Column(
                    name='time',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='elb',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='client_ip',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='client_port',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='target_ip',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='target_port',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='request_processing_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='target_processing_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='response_processing_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='elb_status_code',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='target_status_code',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='received_bytes',
                    type=glue.Schema.BIG_INT),
                glue.Column(
                    name='sent_bytes',
                    type=glue.Schema.BIG_INT),
                glue.Column(
                    name='request_verb',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='request_url',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='request_proto',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='user_agent',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='ssl_cipher',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='ssl_protocol',
                    type=glue.Schema.STRING)
            ],

            partition_keys=[
                glue.Column(
                    name='year',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='month',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='day',
                    type=glue.Schema.INTEGER
                )],

            data_format=glue.DataFormat(
                input_format=glue.InputFormat('org.apache.hadoop.mapred.TextInputFormat'),
                output_format=glue.OutputFormat('org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'),
                serialization_library=glue.SerializationLibrary('org.apache.hadoop.hive.serde2.RegexSerDe')
            )
        )

        logs_table_cfn = logs_table.node.default_child
        logs_table_cfn.add_override('Properties.TableInput.StorageDescriptor.SerdeInfo.Parameters.serialization\.format', 1)
        input_regex = '([^ ]*) ([^ ]*) ([^ ]*):([0-9]*) ([^ ]*)[:\-]([0-9]*) ([-.0-9]*) ([-.0-9]*) ([-.0-9]*) (|[-0-9]*) (-|[-0-9]*) ([-0-9]*) ([-0-9]*) \\\"([^ ]*) ([^ ]*) (- |[^ ]*)\\\" (\"[^\"]*\") ([A-Z0-9-]+) ([A-Za-z0-9.-]*)$'
        logs_table_cfn.add_override('Properties.TableInput.StorageDescriptor.SerdeInfo.Parameters.input\.regex', input_regex)
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.digits', '2')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.range', '01,31')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.digits', '2')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.range', '01,12')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.digits', '4')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.range', '2017,2050')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.enabled', 'true')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.EXTERNAL', 'TRUE')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.storage\.location\.template', f's3://{bucket_name}/{bucket_path}/${{year}}/${{month}}/${{day}}')

        self.__create_named_query(
            f'CLB - TLS Version - 30 days - {clb_table_id}', 'CLB - TLS Version - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, ssl_protocol, ROUND((COUNT(ssl_protocol)* 100.0 / (SELECT COUNT(*) FROM "{clb_table_name}" WHERE ssl_protocol != '-' AND from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT() AS requests
            FROM "{clb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT ssl_protocol = '-'
            GROUP BY elb, ssl_protocol, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'CLB - TLS Ciphersuites - 30 days - {clb_table_id}', 'CLB - TLS Ciphersuites - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, ssl_cipher, ROUND((COUNT(ssl_cipher)* 100.0 / (SELECT COUNT(*) FROM "{clb_table_name}" WHERE ssl_cipher != '-' AND from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT() AS requests
            FROM "{clb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT ssl_cipher = '-'
            GROUP BY elb, ssl_cipher, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'CLB - TLS Version and Ciphersuites combined - 30 days - {clb_table_id}', 'CLB - TLS Version and Ciphersuites combined - 30 days',
            f"""SELECT DISTINCT elb, ssl_cipher, ssl_protocol,  count(ssl_cipher) AS requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND NOT ssl_protocol = '-'
            GROUP BY elb, ssl_cipher,ssl_protocol
            ORDER BY requests DESC""")

        self.__create_named_query(
            f'CLB - Top 10 TLS 1.0 talkers - 30 days - {clb_table_id}', 'CLB - Top 10 TLS 1.0 talkers - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) as requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND ssl_protocol = 'TLSv1'
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'CLB - Top 100 Clients and User Agents for TLS 1.0 - 30 days - {clb_table_id}', 'CLB - Top 10 TLS 1.0 talkers - 30 days',
            f"""SELECT DISTINCT(elb, client_ip, ssl_protocol, user_agent), COUNT(*) AS requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND ssl_protocol = 'TLSv1'
            GROUP BY (elb, client_ip, ssl_protocol, user_agent)
            ORDER BY requests DESC
            LIMIT 100""")

        self.__create_prepared_statement(
            f'clb_tls_version_{clb_table_id}',
            'CLB TLS Version Distribution',
            f"""SELECT elb, ssl_protocol, COUNT() AS requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > date_add('day', ?, current_timestamp)
                AND NOT ssl_protocol = '-'
                AND elb = ?
            GROUP BY elb, ssl_protocol
            """)

        self.__create_named_query(
            f'CLB - Top 10 talkers - 30 days - {clb_table_id}', 'CLB - Top 10 talkers - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) as requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'CLB - Top 10 talkers - Requests - 30 days - {clb_table_id}', 'CLB - Top 10 talkers - Requests - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) AS requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'CLB - Top 10 talkers - Requests - Time Range - {clb_table_id}', 'CLB - Top 10 talkers - Requests - Time Range days',
            f"""SELECT elb, client_ip, COUNT(*) AS requests
            FROM "{clb_table_name}"
            WHERE time >= '2022-09-12T00:00:00.000000Z'
            AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_named_query(
            f'CLB - Top 10 talkers - Megabytes - 30 days - {clb_table_id}', 'CLB - Top 10 talkers - Megabytes - 30 days',
            f"""SELECT elb, client_ip, ROUND(sum(received_bytes/1000000.0),2) as client_data_received_megabytes
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP by elb, client_ip
            ORDER by client_data_received_megabytes DESC;""")

        self.__create_named_query(
            f'CLB - Avg Request/Response size - 30 days - {clb_table_id}', 'CLB - Avg Request/Response size - 30 days',
            f"""SELECT elb, ROUND((avg(sent_bytes)/1000.0 + avg(received_bytes)/1000.0),2) as avg_request_response_kilobytes
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb""")

        self.__create_named_query(
            f'CLB - Target Distribution - 30 days - {clb_table_id}', 'CLB - Target Distribution - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, target_ip, ROUND((Count(target_ip)* 100.0 / (Select Count(*) From "{clb_table_name}" WHERE from_iso8601_timestamp(time) > var.intrvl AND NOT target_ip = '')),2)
            as backend_traffic_percentage
            FROM "{clb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT target_ip = ''
            GROUP by elb, target_ip, var.intrvl
            ORDER By count() DESC;""")

        self.__create_named_query(
            f'CLB - LB 4xx and 5xx errors - 30 days - {clb_table_id}', 'CLB - LB 4xx and 5xx errors - 30 days',
            f"""SELECT * FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                -- WHERE time >= '2022-09-12T00:00:00.000000Z'
                -- AND time <= '2022-09-19T23:59:59.9999999Z'
                -- AND elb_status_code = 400
                AND elb_status_code BETWEEN 400 AND 599;""")

        self.__create_named_query(
            f'CLB - Target 4xx and 5xx errors - 30 days - {clb_table_id}', 'CLB - Target 4xx and 5xx errors - 30 days',
            f"""SELECT * FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                -- WHERE time >= '2022-09-12T00:00:00.000000Z'
                -- AND time <= '2022-09-19T23:59:59.9999999Z'
                -- AND target_status_code = 400
                AND target_status_code BETWEEN 400 AND 599;""")

        self.__create_named_query(
            f'CLB - Client IPs per URL hit - 30 days - {clb_table_id}', 'CLB - Client IPs per URL hit - 30 days',
            f"""SELECT client_ip, elb, request_url, count(*) as count FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP by client_ip, elb, request_url
            ORDER by count DESC;""")

        self.__create_named_query(
            f'CLB - Top 100 user-agents - 30 days - {clb_table_id}', 'CLB - Top 100 user-agents - 30 days',
            f"""SELECT elb, user_agent, COUNT(*) AS requests
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb, user_agent
            ORDER BY requests DESC
            LIMIT 100;""")

        self.__create_named_query(
            f'CLB - Slow Responses - 30 days - {clb_table_id}', 'CLB - Slow Responses - 30 days',
            f"""SELECT * FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            AND target_processing_time >= 5.0""")

        self.__create_named_query(
            f'CLB - Aggregated Log Information - 30 days - {clb_table_id}', 'CLB - Aggregated Log Information - 30 days',
            f"""SELECT elb,
                count(1) AS requestCount,
                min(time) as firstLogTime,
                max(time) as lastLogTime,
                ROUND(avg(received_bytes+sent_bytes)) AS avgTransactionSize,
                sum(received_bytes) as totalBytesReceived,
                sum(sent_bytes) as totalBytesSent,
                sum(received_bytes+sent_bytes) AS totalBytes
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY elb
            ORDER BY requestCount DESC limit 10;""")

        self.__create_named_query(
            f'CLB - Processed Traffic by ELB & Target IP - 30 days - {clb_table_id}', 'CLB - Processed Traffic by ELB & Target IP - 30 days',
            f"""SELECT target_ip AS target,
                count(target_ip) AS count,
                sum(received_bytes) as totalRecvBytes,
                sum(sent_bytes) as totalSentBytes,
                avg(received_bytes)+avg(sent_bytes) AS avgTransactionSize,
                avg(request_processing_time) as avgRequestTime,
                avg(target_processing_time) as avgLatency,
                avg(response_processing_time) as avgResponseTime,
                regexp_extract("$path",'((1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])\.){{3}}(1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])') AS elb_ip,
                approx_percentile(request_processing_time, 0.99) as P99Request,
                approx_percentile(target_processing_time, 0.99) as P99Latency,
                approx_percentile(response_processing_time, 0.99) as P99Response,
                approx_percentile(request_processing_time, 0.999) as P999Request,
                approx_percentile(target_processing_time, 0.999) as P999Latency,
                approx_percentile(response_processing_time, 0.999) as P999Response
            FROM "{clb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            -- WHERE time >= '2022-09-12T00:00:00.000000Z'
            -- AND time <= '2022-09-19T23:59:59.9999999Z'
            GROUP BY  target_ip, regexp_extract("$path",'((1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])\.){{3}}(1?[0-9][0-9]?|2[0-4][0-9]|25[0-5])')
            ORDER BY count DESC limit 1000;
            """)

    def athena_nlb(self, name, bucket_name, **kwargs):
        bkt_acc_id = kwargs.get('bucket_account', self.account)
        bucket_logs = s3.Bucket.from_bucket_name(self, f'nlb_logs_{name}', bucket_name)
                    
        if 'bucket_prefix' in kwargs:
            bucket_prefix = kwargs['bucket_prefix']
            bucket_path = f'{bucket_prefix}/AWSLogs/{bkt_acc_id}/elasticloadbalancing/{self.region}'
            nlb_table_id = f'{bucket_name}_{bucket_prefix}'
            nlb_table_id = nlb_table_id.replace('/', '_')
        else:
            bucket_path = f'AWSLogs/{bkt_acc_id}/elasticloadbalancing/{self.region}'
            nlb_table_id = bucket_name

        nlb_table_id = nlb_table_id.lower()
        nlb_table_name = f'tb_nlb_logs_{nlb_table_id}'

        logs_table = glue.Table(
            self, nlb_table_name,
            database=self.logs_db,
            table_name=nlb_table_name,
            bucket=bucket_logs,
            s3_prefix=bucket_path,
            columns=[
                glue.Column(
                    name='type',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='version',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='time',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='elb',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='listener',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='client_ip',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='client_port',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='destination_ip',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='destination_port',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='connection_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='tls_handshake_time',
                    type=glue.Schema.DOUBLE),
                glue.Column(
                    name='received_bytes',
                    type=glue.Schema.BIG_INT),
                glue.Column(
                    name='sent_bytes',
                    type=glue.Schema.BIG_INT),
                glue.Column(
                    name='incoming_tls_alert',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='chosen_cert_arn',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='chosen_cert_serial',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='tls_cipher',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='tls_protocol_version',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='tls_named_group',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='domain_name',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='alpn_fe_protocol',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='alpn_be_protocol',
                    type=glue.Schema.STRING),
                glue.Column(
                    name='alpn_client_preference_list',
                    type=glue.Schema.STRING)
            ],

            partition_keys=[
                glue.Column(
                    name='year',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='month',
                    type=glue.Schema.INTEGER),
                glue.Column(
                    name='day',
                    type=glue.Schema.INTEGER
                )],

            data_format=glue.DataFormat(
                input_format=glue.InputFormat('org.apache.hadoop.mapred.TextInputFormat'),
                output_format=glue.OutputFormat('org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'),
                serialization_library=glue.SerializationLibrary('org.apache.hadoop.hive.serde2.RegexSerDe')
            )
        )

        logs_table_cfn = logs_table.node.default_child
        logs_table_cfn.add_override('Properties.TableInput.StorageDescriptor.SerdeInfo.Parameters.serialization\.format', 1)
        input_regex = '([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*):([0-9]*) ([^ ]*)[:-]([0-9]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*) ([^ ]*)'
        logs_table_cfn.add_override('Properties.TableInput.StorageDescriptor.SerdeInfo.Parameters.input\.regex', input_regex)
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.digits', '2')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.range', '01,31')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.day\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.digits', '2')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.range', '01,12')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.month\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.digits', '4')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.type', 'integer')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.year\.range', '2017,2050')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.projection\.enabled', 'true')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.EXTERNAL', 'TRUE')
        logs_table_cfn.add_override('Properties.TableInput.Parameters.storage\.location\.template', f's3://{bucket_name}/{bucket_path}/${{year}}/${{month}}/${{day}}')

        self.__create_named_query(
            f'NLB - TLS Version - 30 days - {nlb_table_id}', 'NLB - TLS Version - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, tls_protocol_version, ROUND((COUNT(tls_protocol_version)* 100.0 / (SELECT COUNT(*) FROM "{nlb_table_name}" WHERE tls_protocol_version != '-' AND from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT() AS requests
            FROM "{nlb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT tls_protocol_version = '-'
            GROUP BY elb, tls_protocol_version, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'NLB - TLS Ciphersuites - 30 days - {nlb_table_id}', 'NLB - TLS Ciphersuites - 30 days',
            f"""WITH var as (
                SELECT current_timestamp - interval '30' day as intrvl
            )
            SELECT elb, tls_cipher, ROUND((COUNT(tls_cipher)* 100.0 / (SELECT COUNT(*) FROM "{nlb_table_name}" WHERE tls_cipher != '-' AND from_iso8601_timestamp(time) > var.intrvl)),2) AS percentage, COUNT() AS requests
            FROM "{nlb_table_name}", var
            WHERE from_iso8601_timestamp(time) > var.intrvl
                AND NOT tls_cipher = '-'
            GROUP BY elb, tls_cipher, var.intrvl
            ORDER BY percentage DESC""")

        self.__create_named_query(
            f'NLB - TLS Version and Ciphersuites combined - 30 days - {nlb_table_id}', 'NLB - TLS Version and Ciphersuites combined - 30 days',
            f"""SELECT DISTINCT elb, tls_cipher, tls_protocol_version,  count(tls_cipher) AS requests
            FROM "{nlb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND NOT tls_protocol_version = '-'
            GROUP BY elb, tls_cipher,tls_protocol_version
            ORDER BY requests DESC""")

        self.__create_named_query(
            f'NLB - Top 10 TLS 1.0 talkers - 30 days - {nlb_table_id}', 'NLB - Top 10 TLS 1.0 talkers - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) as requests
            FROM "{nlb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
                AND tls_protocol_version = 'tlsv1'
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")

        self.__create_prepared_statement(
            f'nlb_tls_version_{nlb_table_id}',
            'NLB TLS Version Distribution',
            f"""SELECT elb, tls_protocol_version, COUNT() AS requests
            FROM "{nlb_table_name}"
            WHERE from_iso8601_timestamp(time) > date_add('day', ?, current_timestamp)
                AND NOT tls_protocol_version = '-'
                AND elb = ?
            GROUP BY elb, tls_protocol_version
            """)

        self.__create_named_query(
            f'NLB - Top 10 talkers - 30 days - {nlb_table_id}', 'NLB - Top 10 talkers - 30 days',
            f"""SELECT elb, client_ip, COUNT(*) as requests
            FROM "{nlb_table_name}"
            WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day
            GROUP BY elb, client_ip
            ORDER BY requests DESC
            LIMIT 10""")
