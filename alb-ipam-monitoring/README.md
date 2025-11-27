# ALB IPAM Monitoring Solution

## Overview

A serverless monitoring solution that tracks IP address usage for Application Load Balancers (ALBs) with VPC IPAM (IP Address Management) pools enabled. This CloudFormation template deploys a complete monitoring stack that automatically discovers IPAM-enabled ALBs, performs DNS lookups to identify active IP addresses, validates IPs against IPAM pool CIDR blocks, and provides comprehensive monitoring through CloudWatch Dashboard and Alarms.

## How it works

- **Complete Infrastructure**: CloudFormation template deploys all required resources
- **Automatic Discovery**: Finds all IPAM-enabled ALBs in your AWS account
- **IP Address Counting**: Performs DNS lookups using the "all." prefix to get related load balancer IP addresses
- **IPAM Pool Validation**: Validates IP addresses against IPAM pool CIDR blocks and detects foreign IPs
- **CloudWatch Integration**: Publishes custom metrics with automated dashboard and alarms
- **SNS Notifications**: Optional SNS integration for alarm notifications
- **Configurable Scheduling**: Flexible execution frequency (1, 5, or 10 minutes)

## Prerequisites

- AWS CLI installed and configured with appropriate permissions
- Application Load Balancers (ALB) with IPAM enabled

## Deployment Parameters

| Parameter | Default | Options | Description |
|-----------|---------|---------|-------------|
| `ScheduleExpression` | `rate(1 minute)` | `rate(1 minute)`, `rate(5 minutes)`, `rate(10 minutes)` | Lambda execution frequency |
| `EnableDebugLogging` | `false` | `true`, `false` | Enable verbose logging for troubleshooting |
| `LogRetentionDays` | `30` | `1`, `3`, `5`, `7`, `14`, `30`, `60`, `90`, `120`, `150`, `180`, `365`, `400`, `545`, `731`, `1827`, `3653` | CloudWatch Log Group retention period in days. |
| `SNSTopicArn` | `` | Valid SNS ARN or empty | Optional SNS notifications for alarm state changes |

## Deploy Solution

```bash
REGION=us-east-1  # change to your region
SNS_TOPIC_ARN=    # optional
aws cloudformation --region $REGION create-stack \
  --stack-name alb-ipam-monitoring \
  --template-body file://cloudformation-template.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameters ParameterKey=ScheduleExpression,ParameterValue="rate(1 minute)" \
               ParameterKey=EnableDebugLogging,ParameterValue="false" \
               ParameterKey=LogRetentionDays,ParameterValue="30" \
               ParameterKey=CloudWatchMetricNamespace,ParameterValue="ALB/IPAM-Monitoring" \
               ParameterKey=SNSTopicArn,ParameterValue=$SNS_TOPIC_ARN
```

*Note: Replace the SNS ARN with your actual topic ARN, or omit the SNSTopicArn parameter if Alarm SNS notifications are not needed.*

## Remove Solution Resources

```bash
REGION=us-east-1  # change to your region
aws cloudformation --region $REGION delete-stack --stack-name alb-ipam-monitoring
```

## Usage

1. **Access Dashboard**: Navigate to the AWS Console → CloudWatch → Dashboards → `ALB-IPAM-Monitoring`.
2. **Monitor Alarms**: Check alarm status in the "ALB-IPAM-Monitoring" dashboard or in the CloudWatch → Alarms console. Alarm name: `ALB-IPAM-Monitoring-Foreign-IP-Detected-{region}`.
3. **Review Metrics**: View `IPAddressCount` trends to plan IPAM pool capacity.
4. **View Logs**: Check Lambda execution logs in CloudWatch Log Group: `/aws/lambda/ALB-IPAM-Monitoring`.

## Estimated monthly cost of running the solution: US East (N. Virginia)

**Calculation for 5 Load Balancers:**
- CloudWatch Custom Metrics: 5 LBs × 2 metrics × $0.30 = $3.00
- CloudWatch PutMetricData API calls:
  - 1 minute intervals: = (5 LBs x 2 metrics x 43200)/1000 x $0.01 = $4.32
  - 5 minute intervals = (5 LBs x 2 metrics x 8640)/1000 x $0.01 = $0.86
  - 10 minute intervals = (5 LBs x 2 metrics x 4320)/1000 x $0.01 = $0.43
- CloudWatch Dashboard: $3.00
- CloudWatch GetDashboard, ListDashboards: $0.01 per 1,000 requests
- CloudWatch Metrics Insights Alarm: 5 LBs × $0.10 = $0.50
- CloudWatch Log Storage: ~86 MB/month × $0.01 = $0.86 (estimate based on 30 days log retention, debug logging disabled)
insights query to populate the "Recent Foreign IP Detection Logs" widget)
- Lambda invocation costs:
  - 1 minute intervals: 43,200 invocations × $0.0000002 = $0.009
  - 5 minute intervals: 8,640 invocations × $0.0000002 = $0.002
  - 10 minute intervals: 4,320 invocations × $0.0000002 = $0.001
- Lambda duration costs (128MB, ~5 sec avg):
  - 1 minute intervals: 43,200 × 5 sec × 0.125GB × $0.0000166667 = $0.45
  - 5 minute intervals: 8,640 × 5 sec × 0.125GB × $0.0000166667 = $0.09
  - 10 minute intervals: 4,320 × 5 sec × 0.125GB × $0.0000166667 = $0.045
- CloudWatch Log Insights query charges while actively viewing the dashboard are dependent on the dashboard refresh rate and how much log data exists: $0.005 per GB of data scanned

**Total: $12.31/month**
Similar calculations apply for 5-minute ($8.35) and 10-minute ($7.89) schedules with reduced Lambda/CloudWatch API invocation costs, albeit slower detection and alarming on foreign IP addresses.

*Costs exclude data transfer and may vary by region. See [AWS Pricing Calculator](https://calculator.aws) for detailed estimates.*
https://aws.amazon.com/cloudwatch/pricing
https://aws.amazon.com/lambda/pricing
https://aws.amazon.com/sns/pricing
