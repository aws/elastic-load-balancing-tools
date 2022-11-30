#!/usr/bin/env python3

from configparser import ConfigParser

# from aws_cdk import core
from aws_cdk import App

from stacks.athena_stack import AthenaStack

# Read config.ini file
config_object = ConfigParser()
config_object.read('config.ini')
defaults = config_object['defaults']
region = defaults['region']

app = App()


# Define the stack 
stack = AthenaStack(app, 'AthenaElbLogStack', env={'region': region})
stack.template_options.description = 'Athena & Glue resources for ELB Access Logs analysis'

# Example for ALB 
alb_bucket_name = 'my-alb-access-log-s3-bucket'
stack.athena_alb('main_alblogs_bucket1', alb_bucket_name)

# Example for CLB 
clb_bucket_name = 'my-clb-access-log-s3-bucket'
stack.athena_clb('main_clblogs_bucket1', clb_bucket_name)

# Example for NLB 
nlb_bucket_name = 'my-nlb-access-log-s3-bucket'
stack.athena_nlb('main_nlblogs_bucket1', nlb_bucket_name)

# Example for ALB/ Cross-account, adding bucket_account parameter with the account id
alb_bucket_name = 'my-alb-access-log-s3-bucket-in-otheraccount'
stack.athena_alb('main_logs_cross_account', alb_bucket_name, bucket_account='123456789012')

# Example with specific prefix in the S3 bucket, add bucket_prefix parameter
alb_bucket_name = 'my-alb-access-log-s3-bucket'
stack.athena_alb('main_logs_cross_account', alb_bucket_name, bucket_prefix='myalb1')

app.synth()
