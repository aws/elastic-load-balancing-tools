# ALB mTLS Trust Store Monitoring Solution

## Overview

A serverless monitoring solution that tracks Application Load Balancer (ALB) mTLS trust store usage and capacity. This CloudFormation template deploys a complete monitoring stack that automatically discovers all trust stores in your AWS account, retrieves certificate and revocation count, calculates the CA certificate bundles total subject size, and provides comprehensive monitoring through CloudWatch Dashboard and Alarms.

## How it works

- **Complete Infrastructure**: CloudFormation template deploys all required resources
- **Automatic Discovery**: Finds all ALB mTLS trust stores in your AWS account and region
- **Certificate Analysis**: Analyzes the CA certificate bundles to calculate subject size
- **CloudWatch Integration**: Publishes 3 custom metrics per trust store with automated dashboard and alarms
- **Dynamic Thresholds**: Queries AWS Service Quotas API to automatically calculate alarm thresholds based on current quota limits and user defined percentage thresholds.
- **SNS Notifications**: Optional SNS integration for alarm notifications

## Prerequisites

- AWS CLI installed and configured with appropriate permissions
- Application Load Balancers (ALB) with mTLS trust stores configured
- Lambda Layer with cryptography library (created via `create-lambda-layer.sh`)

## Deployment Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `EnableDebugLogging` | `false` | Enable verbose logging for troubleshooting |
| `LogRetentionDays` | `30` | CloudWatch Log Group retention period in days |
| `CloudWatchMetricNamespace` | `ALB/TrustStore-Monitoring` | CloudWatch metric namespace for custom metrics |
| `CaCertificatesThresholdPercentage` | `90` | Percentage of Service Quota limit for "CA certificates per trust store" alarm threshold (1-100) |
| `RevokedEntriesThresholdPercentage` | `90` | Percentage of Service Quota limit for "Revocation entries per trust store" alarm threshold (1-100) |
| `SubjectSizeThresholdPercentage` | `90` | Percentage of Service Quota limit for "CA certificates subject size per trust store" alarm threshold (1-100) |
| `CryptographyLayerArn` | *(required)* | ARN of the Lambda Layer containing the cryptography library |
| `SNSTopicArn` | `` | Optional SNS notifications for alarm state changes |

## Deploy Solution

### Step 1: Create Lambda Layer

```bash
./create-lambda-layer.sh us-east-1  # change to your region
```

**Notes:**

* This script takes the AWS region as a parameter and creates a Lambda Layer with the required Python cryptography library. Note the Layer ARN from the output.

* Copy the script contents and run from CloudShell for ease of use

### Step 2: Deploy CloudFormation Stack

```bash
REGION=us-east-1  # change to your region
LAYER_ARN=        # ARN from Step 1
SNS_TOPIC_ARN=    # optional

aws cloudformation --region $REGION create-stack \
  --stack-name alb-trust-store-monitoring \
  --template-body file://cloudformation-template.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameters ParameterKey=EnableDebugLogging,ParameterValue="false" \
               ParameterKey=LogRetentionDays,ParameterValue="30" \
               ParameterKey=CloudWatchMetricNamespace,ParameterValue="ALB/TrustStore-Monitoring" \
               ParameterKey=CaCertificatesThresholdPercentage,ParameterValue="90" \
               ParameterKey=RevokedEntriesThresholdPercentage,ParameterValue="90" \
               ParameterKey=SubjectSizeThresholdPercentage,ParameterValue="90" \
               ParameterKey=CryptographyLayerArn,ParameterValue=$LAYER_ARN \
               ParameterKey=SNSTopicArn,ParameterValue=$SNS_TOPIC_ARN
```

**Notes:**

* Replace the SNS_TOPIC_ARN with your actual topic ARN, or omit the SNSTopicArn parameter if Alarm SNS notifications are not needed.

## Remove Solution Resources

```bash
REGION=us-east-1  # change to your region
aws cloudformation --region $REGION delete-stack --stack-name alb-trust-store-monitoring
```

## Usage

1. **Access Dashboard**: Navigate to the AWS Console → CloudWatch → Dashboards → `ALB-TrustStore-Monitoring-{region}`.
2. **Monitor Alarms**: Check alarm status in the "ALB-TrustStore-Monitoring-{region}" dashboard or in the CloudWatch → Alarms console.
3. **Review Metrics**: View trust store capacity trends to plan for Service Quota increases.
4. **View Logs**: Check Lambda execution logs in CloudWatch Log Group: `/aws/lambda/ALB-TrustStore-Monitoring`.

## Monitored Metrics

- **NumberOfCaCertificates**: Total CA certificates in each trust store
- **TotalRevokedEntries**: Count of revoked certificates in each trust store
- **TotalSubjectSize**: Total size of certificate subjects in bytes for each trust store

## CloudWatch Alarms

The solution creates the following CloudWatch Alarms (names include the AWS region):

- **ALB-TrustStore-Monitoring-CaCertificates-{region}**: Triggers when CA certificates count exceeds the configured percentage of Service Quota limit
- **ALB-TrustStore-Monitoring-RevokedEntries-{region}**: Triggers when revoked entries count exceeds the configured percentage of Service Quota limit
- **ALB-TrustStore-Monitoring-SubjectSize-{region}**: Triggers when total subject size exceeds the configured percentage of Service Quota limit

**Notes:**

* Default threshold for each is 90% of the current quota value. This percentage is configurable when launching the CloudFormation template.

* Alarm thresholds are automatically updated by the Lambda function on each execution, based on current Service Quota values and the configured percentage parameters. Thresholds are always rounded down to whole numbers.

## Estimated monthly cost of running the solution: US East (N. Virginia)

**Calculation for 1 Trust Store (hourly schedule):**
- CloudWatch Custom Metrics: 1 trust store × 3 metrics × $0.30 = $0.90
- CloudWatch PutMetricData API calls: (1 trust store × 3 metrics × 720 hours)/1000 × $0.01 = $0.022
- CloudWatch Dashboard: $3.00
- CloudWatch Alarms: 1 trust store × 3 alarms × $0.10 = $0.30
- CloudWatch Log Storage: ~1-5 MB/month × $0.50/GB = $0.001-0.003 (estimate based on 30 days log retention, debug logging disabled)
- Lambda invocation costs: 720 invocations × $0.0000002 = $0.00014
- Lambda duration costs (256MB memory, ~2 seconds avg): 720 × 2 sec × 0.25GB × $0.0000166667 = $0.006

**Total: ~$4.23/month**

*Costs exclude data transfer and may vary by region. See [AWS Pricing Calculator](https://calculator.aws) for detailed estimates.*
- https://aws.amazon.com/cloudwatch/pricing
- https://aws.amazon.com/lambda/pricing
- https://aws.amazon.com/sns/pricing
