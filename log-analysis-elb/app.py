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

alb_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
stack.athena_alb('main_logs_bucket1', alb_bucket_name)

# alb_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
# stack.athena_alb('main_logs_bucket2', alb_bucket_name, bucket_prefix='myalb2')

# clb_bucket_name = 'elbstack1-clblogselbstack1569130d6-krxi15z4lzxn'
# stack.athena_clb('main_logs', clb_bucket_name)

# nlb_bucket_name = 'elbstack1-nlblogselbstack1a07805c6-1ephxwcaha4pc'
# stack.athena_nlb('main_logs', nlb_bucket_name)

# Cross account example, add bucket_account parameter
# alb_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
# stack.athena_alb('main_logs_cross_account', alb_bucket_name, bucket_account='174029014086')

# Example with specific prefix, add bucket_prefix parameter
# alb_bucket_name = 'elbstack1-alblogselbstack1b1061897-1wgbmokmrr0me'
# stack.athena_alb('main_logs_cross_account', alb_bucket_name, bucket_prefix='myalb1')

app.synth()
