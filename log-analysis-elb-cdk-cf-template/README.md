# ELB Log Analysis

## Overview

This cdk / CloudFormation project aims to make it easy to spin up the resources needed to query ELB logs using Athena/SQL.

Along with the Glue and Athena resources, it also creates pre-defined queries to help with specific use cases:

- Find Top Clients / User Agents
- Stats for Target Distribution / Request size / URL hit
- Search for 4xx and 5xx HTTP errors
- Understand TLS protocol / ciphersuite usage

### Architecture

![diagram](images/ELBLogAnalysis.png)

## Using CloudFormation Templates

Use the following templates:

- **ALB**: templates/alb_logs.yaml
- **CLB**: templates/clb_logs.yaml
- **NLB**: templates/nlb_logs.yaml

Provide the Bucket Name where the ELB logs are as a parameter for the CloudFormation template.

## Using CDK

If you are not familiar with AWS CDK, start here: https://docs.aws.amazon.com/cdk/v2/guide/home.html

### Set up

1 - Clone this repository
1 - Create and activate a virtualenv:
```
python3 -m venv .venv
source .venv/bin/activate
```
1 - Install dependencies:

```
pip install -r requirements.txt 
```


### Configuring the region: 
Edit the file *config.ini* to set your region:

```
[defaults]
region = us-east-1
```

### Configure credentials
CDK will use the default AWS profile credentials configured in your machine, you can change it or add credentials as environment variables:
```
export AWS_ACCESS_KEY_ID=<INSERT_YOUR_ACCESS_KEY_ID>
export AWS_SECRET_ACCESS_KEY=<INSERT_YOUR_SECRET_ACCESS_KEY>

Like the example below:

export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

### Run cdk bootstrap
Because the template is larger than 50Kb, *cdk bootstrap* is required, this will create a bucket in the account to facilitate the template upload.

```cdk bootstrap```


### Setting up stacks

Open the file app.py and edit it as per the instructions below.

#### Creating a stack :

```stack = AthenaStack(app, 'AthenaStack1', env={'region': region})```

The line above defines a new stack *AthenaStack1*. To add the actual resources to your stack you must call one of the methods according to the ELB type (athena_alb, athena_clb or athena_nlb), passing the S3 bucket where your ELB access logs are as parameters.


*stack.athena_alb(identifier, bucket_name)*

*stack.athena_clb(identifier, bucket_name)*

*stack.athena_nlb(identifier, bucket_name)*

In the example below we are creating three sets of resources, for each type of Load Balancer:

```
stack = AthenaStack(app, 'AthenaStack1', env={'region': region})

alb_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
stack.athena_alb('main_logs', alb_bucket_name)

clb_bucket_name = 'elbstack1-clblogselbstack1569130d6-krxi15z4lzxn'
stack.athena_clb('main_logs', clb_bucket_name)

nlb_bucket_name = 'elbstack1-nlblogselbstack1a07805c6-1ephxwcaha4pc'
stack.athena_nlb('main_logs', nlb_bucket_name)
```


If you have multiple buckets for same ELB type, you can call same method again for each one of them, just changing the name identifier:


```
alb_main_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
stack.athena_alb('main_logs', alb_main_bucket_name)

alb_other_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
stack.athena_alb('other_logs', alb_other_bucket_name)
```

Cross account support:

If the account where the bucket with the logs are is different than the account where you are creating this stack, you have to provide the parameter *bucket_account* with the account id of the bucket:
```
alb_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
stack.athena_alb('main_logs_cross_account', alb_bucket_name, bucket_account='174029014086')
```

See *Cross Account permissions* section below for information on how to set the permissions on the bucket for cross account access.


### Deploy

To deploy this stack to your default AWS account/region, run:

```cdk deploy```

### Usage
Once the stack is deployed sucesfully, open your AWS Console and in the navigation bar search for Athena, open it and observe the select the option as described below and shown in the image.

1 - Database should be **db_elb_logs_<STACK_NAME>**

2 - Tables should be one per S3 bucket with logs as defined in the app.py file.

3 - Select the correct workgroup, should look like **wg_elb_logs_<STACK_NAME>**


![step1](images/step1.png)

- Next, open the tab "Saved queries" to access the pre-created queries:

![step2](images/step2.png)

- Select the type of query and ELB you want to run, this will open a window like shown below:

![step3](images/step3.png)

- In the window above, you can hit the Run (or Run again) button to execute the query. 

You can also adjust the SQL statement to your needs, for example to change for a larger period (60 days), replace:

```WHERE from_iso8601_timestamp(time) > current_timestamp - interval '30' day```

to 

```WHERE from_iso8601_timestamp(time) > current_timestamp - interval '60' day```

### Cross Account permissions

To allow cross account access there are two steps:

1 - **Disable ACL**

In the Bucket permissions, select ACLs Disabled:

![step3](images/disable-acl.png)

This will enforce bucket owner as the owner of all objects (log files), otherwise the objects will be owned by ELB Access delivery account and the cross account sharing will not work.


2 - **Add bucket policy statement**

```
    {
            "Sid": "CrossAccount",
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::<source_account_id>:root"
            },
            "Action": [
                "s3:GetObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::<bucket_name>",
                "arn:aws:s3:::<bucket_name>/AWSLogs/<account_id>/*"
            ]
        }
```
Replace <bucket_name>, <source_account_id> and <account_id>

source_account_id is the account where the Athena Stack is going to be created.

### License

This project is licensed under the Apache 2.0 License: https://www.apache.org/licenses/LICENSE-2.0 .