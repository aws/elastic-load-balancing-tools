#!/usr/bin/env python
# Copyright 2016. Amazon Web Services, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Import the SDK and required libraries
import boto3
import logging
import argparse
from pprint import pprint
import json
import sys
import botocore


# Classic load balancer to Application Load Balancer copy utility
# version 1.1.0 2016
# Authors: Long Ren, Dan Lindow, Max Clements, Tipu Qureshi

# This script uses the configuration of the specified Classic load balancer
# to create an Application Load Balancer in a "best effort" manner.
# Optionally, it can register existing backend instances as targets.
# Tags and health checks are replicated for each target group.

# With no parameters or configuration, boto3 looks for access keys here:
#
#    1. Environment variables (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)
#    2. Credentials file (~/.aws/credentials or
#         C:\Users\USER_NAME\.aws\credentials)
#    3. AWS IAM role for Amazon EC2 instance
#       (http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)

# Usage:
# copy_classic_load_balancer.py
# --name <value>
# --region <value>
# [--debug <value>]
# [--register-targets]
# [--dry-run]

VERSION = '1.1.0'
# raw_input is now called input in python3, this allows backward compatability
try:
    input = raw_input
except NameError:
    pass

# Returns True if ALB name already exists, False if it does not


def alb_exist(load_balancer_name, region):
    if debug:
        print('checking if ALB exists')
    try:
        response = client.describe_load_balancers(Names=[load_balancer_name])
    except botocore.exceptions.ClientError as e:
        if 'LoadBalancerNotFound' in e.response['Error']['Code']:
            return False
    else:
        return True

# Describe the load balancer and retrieve attributes


def get_elb_data(elb_name, region):
    if debug:
        print("Getting existing Classic ELB data")
    elbc = boto3.client('elb', region_name=region)
    # Describes the specified the load balancer.
    try:
        describe_load_balancers = elbc.describe_load_balancers(
            LoadBalancerNames=[elb_name])
    except botocore.exceptions.ClientError as e:
        if 'LoadBalancerNotFound' in e.response['Error']['Code']:
            print('Cannot find a Classic load balancer in region {} named {}'.format(
                region, elb_name))
            if alb_exist(elb_name, region):
                print('Your load balancer {} is already an Application Load Balancer in {}'.format(
                    elb_name, region))
                sys.exit(1)
            else:
                sys.exit(1)
        else:
            print(e)
    # Describes the attributes for the specified load balancer.
    describe_load_balancer_attributes = elbc.describe_load_balancer_attributes(
        LoadBalancerName=elb_name)

    # Describes the specified policies.
    describe_load_balancer_policies = elbc.describe_load_balancer_policies(
        LoadBalancerName=elb_name)

    # Describes the tags associated with the specified load balancers.

    describe_tags = elbc.describe_tags(
        LoadBalancerNames=[elb_name])

    # Render a dictionary that contains load balancer attributes
    elb_data = {}
    elb_data.update(describe_load_balancers)
    elb_data.update(describe_load_balancer_attributes)
    elb_data.update(describe_load_balancer_policies)
    elb_data.update(describe_tags)
    if debug:
        print("elb data:")
        pprint(elb_data)
    return elb_data


# Define hard failure cases
def passed_hardfailure_detector(elb_data):
    if debug:
        print("Checking hard failure detector")
    # if there are any errors below we will change this to True, else continue
    error = None

    # 1. Verify source load balancer does not have TCP or SSL listeners
    for listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
        if (listener['Listener']['Protocol'] == "TCP") or listener['Listener']['Protocol'] == "SSL":
            print("TCP and SSL listeners are not supported on Application Load Balancer.")
            error = True

    # 2. Verify source load balancer is not in EC2-Classic, 3. Verify source
    # load balancer has at least two enabled subnets
    if 'VPCId' in elb_data['LoadBalancerDescriptions'][0]:
        if len(elb_data['LoadBalancerDescriptions'][0]['Subnets']) >= 2:
            pass
        else:
            print("Error: The Classic load balancer has 1 enabled subnet.\
 A minimum of 2 subnets is required for an Application Load Balancer.")
            error = True
    else:
        print("Error: The Classic load balancer is in EC2-Classic instead of a VPC.\
 A VPC is required for an Application Load Balancer.")
        error = True

    # 4. Verify source load balancer does not use TCP or SSL health checks
    if ('TCP' in elb_data['LoadBalancerDescriptions'][0]['HealthCheck']['Target']) or (
            'SSL' in elb_data['LoadBalancerDescriptions'][0]['HealthCheck']['Target']):
        print("Error: The Classic load balancer uses TCP or SSL health checks.\
 HTTP or HTTPS health checks are required for an Application Load Balancer.")
        error = True

    # 5. Verify unique backend ports is less than 50
    if len(elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']) >= 50:
        backend_ports = []
        for listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
            if listener['Listener']['InstancePort'] not in backend_ports:
                backend_ports.append(listener['Listener']['InstancePort'])
        if len(backend_ports) >= 50:
            print("Error: The number of unique backend ports exceeds 50. The default limit for target groups is 50.")
            error = True

        # 6 Verify that the number of listeners is less than the default
    if len(elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']) >= 10:
        print("Error: The number of listeners exceeds the default limit for an Application Load Balancer.")

        # 7. If Application-Controlled sticky policies are present
    for elb_listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
        if len(elb_listener['PolicyNames']) > 0:
            if 'AppCookieStickinessPolicy' in elb_listener['PolicyNames'][0]:
                print("Error: The Classic load balancer has Application-Controlled stickiness policy.\
 Application-Controlled stickiness policy is not supported on Application Load Balancer.")
                error = True

        # 8. Check for backend authentication on HTTPS backend ports
    if elb_data['LoadBalancerDescriptions'][0]['Policies']['OtherPolicies'] > 0:
        for policy in elb_data['LoadBalancerDescriptions'][0]['Policies']['OtherPolicies']:
            if 'BackendAuthenticationPolicy' in policy:
                print("Error: The Classic load balancer has Backend HTTPS authentication.\
 Backend HTTPS authentication is not supported on Application Load Balancer.")
                error = True

    if error:
        return False
    else:
        return True


def passed_softfailure_detector(elb_data):
    if debug:
        print('Checking soft failure detector')
    # error will change to True if any failure conditions are found
     # 1. If The expiration period of Duration-Based stickiness policy is more than 7 days,
     # we will override its value to 7 day.
    error = None
    for elb_listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
        if len(elb_listener['PolicyNames']) > 0:
            if 'LBCookieStickinessPolicy' in elb_listener['PolicyNames'][0]:
                for policy in elb_data['PolicyDescriptions']:
                    if elb_listener['PolicyNames'][0] in policy.values():
                        # Check if the expiration period  is larger than 7 days
                        if (int(policy['PolicyAttributeDescriptions'][0]['AttributeValue']) > 604800):
                            # Overide the expiration to 7days
                            policy['PolicyAttributeDescriptions'][
                                0]['AttributeValue'] = '604800'
                            print("The maximum expiration period of Duration-Based stickiness policy that Application Load Balancer supports is 7 days.\
Your Application Load Balancer will be created with 7-day expiration period Duration-Based stickiness policy.")
    # 2. If unsupported policy attribute is detected we prompt user if we want
    # to continue.
    supported_attributes = [
        'ConnectionDraining', 'CrossZoneLoadBalancing', 'ConnectionSettings', 'AccessLog']
    for key in elb_data['LoadBalancerAttributes']:
        if key not in supported_attributes:
            answer = input(
                '{} is not supported for an Application Load Balancer. Continue anyway? (y/n)'.format(key))
            if answer.lower() == 'y':
                pass
            else:
                print(
                    "We will not create an Application Load Balancer. Stopping the script.")
                error = True
    # 3. Check for AWS reserved tag
    if len(elb_data['TagDescriptions']) > 0:
        #this creates a copy of the list and allows us to iterate over the copy so we cand modify the original
        for tag in elb_data['TagDescriptions'][0]['Tags'][:]:
            if tag['Key'].startswith('aws:'):
                print("AWS reserved tag is in use. The aws: prefix in your tag names or \
values because it is reserved for AWS use -- http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html#tag-restrictions")
                print('Tag key: {}'.format(tag['Key']))
                answer = input(
                    "Do you want to proceed without AWS reserved tag? y/n ")
                if answer.lower() == 'y':
                    elb_data['TagDescriptions'][0]['Tags'].remove(tag)
                    pass
                else:
                    print(
                        "We will not clone the ELB to an Application Load Balancer. stopping script.")
                    error = True
    if error:
        return False
    else:
        return True

# render a dictionary which contains Application Load Balancer attributes


def get_alb_data(elb_data, region, load_balancer_name):
    if debug:
        print("building the Application Load Balancer data structure")
    # this is used for building the load balancer spec
    alb_data = {'VpcId': elb_data['LoadBalancerDescriptions'][0]['VPCId'], 'Region': region,
                'Alb_name': elb_data['LoadBalancerDescriptions'][0]['LoadBalancerName'],
                'Subnets': elb_data['LoadBalancerDescriptions'][0]['Subnets'],
                'Security_groups': elb_data['LoadBalancerDescriptions'][0]['SecurityGroups'],
                'Scheme': elb_data['LoadBalancerDescriptions'][0]['Scheme'],
                'Tags': elb_data['TagDescriptions'][0]['Tags'],
                'listeners': [],
                'target_group_attributes': [],
                'target_group_arns': []}

    # this is used for building the listeners specs
    for elb_listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
        # If there is a LBCookieStickinessPolicy, append TG attriubtes
        if len(elb_listener['PolicyNames']) > 0:
            if 'LBCookieStickinessPolicy' in elb_listener['PolicyNames'][0]:
                for policy in elb_data['PolicyDescriptions']:
                    if elb_listener['PolicyNames'][0] == policy['PolicyName']:
                        listener = {'Protocol': elb_listener['Listener']['Protocol'],
                                    'Port': elb_listener['Listener']['LoadBalancerPort'],
                                    'TargetGroup_Port': elb_listener['Listener']['InstancePort'],
                                    'TargetGroup_Protocol': elb_listener['Listener']['InstanceProtocol']}
                        TargetGroup_Attribute = {
                            'dereg_timeout_seconds_delay': str(elb_data['LoadBalancerAttributes']['ConnectionDraining']['Timeout']),
                            'stickiness.enabled': 'true',
                            'stickiness.type': 'lb_cookie',
                            'stickiness_policy': policy['PolicyName'].split('-')[3],
                            'stickiness.lb_cookie.duration_seconds': policy['PolicyAttributeDescriptions'][0]['AttributeValue'],
                            'TargetGroup_Port': elb_listener['Listener']['InstancePort']
                        }
                        if listener['Protocol'] == "HTTPS":
                            listener['Certificates'] = [
                                {'CertificateArn': elb_listener['Listener']['SSLCertificateId']}]
            else:
                listener = {'Protocol': elb_listener['Listener']['Protocol'],
                            'Port': elb_listener['Listener']['LoadBalancerPort'],
                            'TargetGroup_Port': elb_listener['Listener']['InstancePort'],
                            'TargetGroup_Protocol': elb_listener['Listener']['InstanceProtocol']}
                TargetGroup_Attribute = {
                    'dereg_timeout_seconds_delay': str(elb_data['LoadBalancerAttributes']['ConnectionDraining']['Timeout']),
                    'TargetGroup_Port': elb_listener['Listener']['InstancePort']
                }
                if listener['Protocol'] == "HTTPS":
                    listener['Certificates'] = [
                        {'CertificateArn': elb_listener['Listener']['SSLCertificateId']}]
            # TGs is not per unique backend port as two TGs might have two
            # different stickiness policy
            alb_data['listeners'].append(listener)
            alb_data['target_group_attributes'].append(TargetGroup_Attribute)
    # this is used for building the target groups
    '''
    # We need to create more target group if ELB front port has Duration-Based sticky policy
    # '''

    alb_data['target_groups'] = []
    hc_target = elb_data['LoadBalancerDescriptions'][
        0]['HealthCheck']['Target']
    # Append unique stickiness policy name to Target Group Name
    for listener in alb_data['listeners']:
        for target_group_attribute in alb_data['target_group_attributes']:
            target_group = {'HealthCheckTimeoutSeconds': elb_data[
                'LoadBalancerDescriptions'][0]['HealthCheck']['Timeout']}
            # We only offer 15 seconds minimum health check interval
            if elb_data['LoadBalancerDescriptions'][0]['HealthCheck']['Interval'] < 15:
                print(
                    "HealthCheck Interval is less than 15 seconds! Setting it to 15 seconds")
                target_group['HealthCheckIntervalSeconds'] = 15
            else:
                target_group['HealthCheckIntervalSeconds'] = elb_data['LoadBalancerDescriptions'][0]['HealthCheck'][
                    'Interval']
            target_group['HealthyThresholdCount'] = elb_data['LoadBalancerDescriptions'][0]['HealthCheck'][
                'HealthyThreshold']
            target_group['UnhealthyThresholdCount'] = elb_data['LoadBalancerDescriptions'][0]['HealthCheck'][
                'UnhealthyThreshold']
            target_group['HealthCheckPath'] = '/' + hc_target.split('/', 1)[1]
            target_group['HealthCheckPort'] = hc_target[
                hc_target.index(':') + 1: hc_target.index('/')]
            target_group['HealthCheckProtocol'] = hc_target.split(':')[0]
            target_group['VpcId'] = elb_data[
                'LoadBalancerDescriptions'][0]['VPCId']
            if listener['TargetGroup_Port'] == target_group_attribute['TargetGroup_Port']:
                target_group['Port'] = listener['TargetGroup_Port']
                target_group['Protocol'] = listener['TargetGroup_Protocol']
                if 'stickiness.type' in target_group_attribute:
                    target_group['Name'] = load_balancer_name[: 23] + "-tg-" + str(
                        listener['TargetGroup_Port']) + "-" + (target_group_attribute['stickiness_policy'])
                    # Only append unique Target Group
                    if not any(tg['Name'] == target_group['Name'] for tg in alb_data['target_groups']):
                        alb_data['target_groups'].append(target_group)
                else:
                    target_group['Name'] = load_balancer_name[
                        : 23] + "-tg-" + str(listener['TargetGroup_Port'])
                    # Only append unique Target Group
                    if not any(tg['Name'] == target_group['Name'] for tg in alb_data['target_groups']):
                        alb_data['target_groups'].append(target_group)

    # create alb attributes
    alb_data['attributes'] = []
    attributes = []
    attribute = {'Key': 'idle_timeout.timeout_seconds',
                 'Value': str(elb_data['LoadBalancerAttributes']['ConnectionSettings']['IdleTimeout'])}
    attributes.append(attribute)
    if elb_data['LoadBalancerAttributes']['AccessLog']['Enabled']:
        attribute = {'Key': 'access_logs.s3.enabled',
                     'Value': str(elb_data['LoadBalancerAttributes']['AccessLog']['Enabled']).lower()}
        attributes.append(attribute)
        attribute = {'Key': 'access_logs.s3.bucket',
                     'Value': elb_data['LoadBalancerAttributes']['AccessLog']['S3BucketName']}
        attributes.append(attribute)
        # we don't specify the prefix key if the prefix is root
        if elb_data['LoadBalancerAttributes']['AccessLog']['S3BucketPrefix'] != '':
            attribute = {'Key': 'access_logs.s3.prefix',
                         'Value': elb_data['LoadBalancerAttributes']['AccessLog']['S3BucketPrefix']}
            attributes.append(attribute)
    alb_data['attributes'] = attributes
    alb_data['instanceIds'] = []
    for instance in elb_data['LoadBalancerDescriptions'][0]['Instances']:
        alb_data['instanceIds'].append(instance['InstanceId'])
    if debug:
        print("alb_data:")
        pprint(alb_data)
    return alb_data


# Create ALB
def create_alb(alb_data):
    if debug:
        print("Creating the Application Load Balancer")
    request = {'Name': alb_data['Alb_name'], 'Subnets': alb_data['Subnets'],
               'SecurityGroups': alb_data['Security_groups'], 'Scheme': alb_data['Scheme']}
    if len(alb_data['Tags']) >= 1:
        request['Tags'] = alb_data['Tags']
    response = client.create_load_balancer(**request)
    if debug:
        print("Create Application Load Balancer response:")
        pprint(response)
    return response['LoadBalancers'][0]['LoadBalancerArn']


# Create Target Group
def create_target_groups(alb_data):
    if debug:
        print("Creating the target groups")
    target_groups = []
    for target_group in alb_data['target_groups']:
        response = client.create_target_group(**target_group)
        if debug:
            print("Create target group %s response: " % (target_group['Name']))
            pprint(response)
        # we store some meta data about each target group, this is used binding
        # the listener to the TG
        for tg in response['TargetGroups']:
            TargetGroup_Arn = {'arn': tg['TargetGroupArn'],
                               'backend_port': tg['Port']}
            alb_data['target_group_arns'].append(TargetGroup_Arn)
    return alb_data['target_group_arns']


# Create ALB Listener
def create_listeners(alb_arn, alb_data, target_groups):
    if debug:
        print("Getting listeners")
    for listener in alb_data['listeners']:
        # this is how we know which listener gets bound to which target group
        for target_group in target_groups:
            if target_group['backend_port'] == listener['TargetGroup_Port']:
                listener['DefaultActions'] = [
                    {'TargetGroupArn': target_group['arn'], 'Type': 'forward'}]
                # Remove these, else the call will fail.
                listener.pop('TargetGroup_Protocol', None)
                listener.pop('TargetGroup_Port', None)
                response = client.create_listener(
                    LoadBalancerArn=alb_arn, **listener)
                if debug:
                    print("Create listener(%s) response: " %
                          (listener['Port']))
                    pprint(response)
                break
    return


# Inherit ELB's attributes
def load_attributes(alb_data, alb_arn):
    if debug:
        print("Adding Application Load Balancer attributes")
    if len(alb_data['attributes']) >= 1:
        response = client.modify_load_balancer_attributes(
            LoadBalancerArn=alb_arn, Attributes=alb_data['attributes'])
    if debug:
        print("Modify load balancer attributes response:")
        pprint(response)
    return


# Configure target group's attributes
def target_group_attributes(alb_data, alb_arn):
    # Create a configuration dict of Target Group attributes
    attributes = {}
    for target_group_attribute in alb_data['target_group_attributes']:
        for tg_arn in alb_data['target_group_arns']:
            # If target_group_attribute has stickiness_policy
            # Make sure each attribute set matches each target group
            if ('stickiness_policy' in target_group_attribute):
                if ((target_group_attribute['stickiness_policy']) in tg_arn['arn']):
                    if tg_arn['arn'] not in attributes:
                        attributes[tg_arn['arn']] = [{'Key': 'deregistration_delay.timeout_seconds', 'Value': target_group_attribute['dereg_timeout_seconds_delay']},
                                                     {'Key': 'stickiness.enabled', 'Value': target_group_attribute[
                                                         'stickiness.enabled']},
                                                     {'Key': 'stickiness.type', 'Value': target_group_attribute[
                                                         'stickiness.type']},
                                                     {'Key': 'stickiness.lb_cookie.duration_seconds', 'Value': target_group_attribute[
                                                         'stickiness.lb_cookie.duration_seconds']}
                                                     ]
            else:
                arn_name = tg_arn['arn'].split('/')[1]
                if (arn_name.count('-')) == 2:
                    # Check if target arn has stickiness policy
                    if tg_arn['arn'] not in attributes:
                        attributes[tg_arn['arn']] = [
                            {'Key': 'deregistration_delay.timeout_seconds', 'Value': target_group_attribute['dereg_timeout_seconds_delay']}]
     # Configure Target Group attributes
    for arn in attributes:
        response = client.modify_target_group_attributes(
            TargetGroupArn=arn, Attributes=attributes[arn])
        if debug:
            print("Modify target group attributes response: ")
            pprint(response)
    return


# Add tag to ELB and Target Group
def add_tags(alb_data, alb_arn, target_groups):
    if debug:
        print("Tagging the Application Load Balancer and target groups")
    if len(alb_data['Tags']) >= 1:
        for target_group in target_groups:
            client.add_tags(ResourceArns=[target_group[
                            'arn']], Tags=alb_data['Tags'])
        client.add_tags(ResourceArns=[alb_arn], Tags=alb_data['Tags'])
    return


# Register back-ends
def register_backends(target_groups, alb_data):
    if debug:
        print("Registering targets with the Application Load Balancer")
    if len(alb_data['instanceIds']) >= 1:
        for target_group in target_groups:
            targets = []
            for instance in alb_data['instanceIds']:
                target = {'Id': instance}
                targets.append(target)
            response = client.register_targets(
                TargetGroupArn=target_group['arn'], Targets=targets)
            if debug:
                print("Register targets response:")
                pprint(response)
    return

# Taking in args in main function


def main():
    """

    :rtype: int
    """
    parser = argparse.ArgumentParser(
        description='Create an Application Load Balancer from a Classic load balancer', usage='%(prog)s --name <elb name> --region')
    parser.add_argument(
        "--name", help="The name of the Classic load balancer", required=True)
    parser.add_argument("--region", help="The region of the Classic load balancer (will also be used for the Application Load Balancer)",
                        required=True)
    parser.add_argument("--debug", help="debug mode", action='store_true')
    parser.add_argument("--register-targets", help="Register the backend instances of the Classic load balancer with the Application Load Balancer",
                        action='store_true')
    parser.add_argument("--dry-run", help="Validate that the current load balancer configuration is compatible\
with Application Load Balancers, but do not perform create operations",
                        action='store_true')
    # if no options, print help
    if len(sys.argv[1:]) == 0:
        parser.print_help()
        parser.exit()
    args = parser.parse_args()
    load_balancer_name = args.name
    region = args.region

    # setting up debugging
    global debug
    debug = False
    if args.debug:
        debug = True
        logging.basicConfig(level=logging.INFO,
                            format='%(asctime)s %(levelname)s %(message)s')
    global client
    session = botocore.session.get_session()
    session.user_agent_name = 'CopyClassicLoadBalancer/' + VERSION
    client = session.create_client('elbv2', region_name=region)

    # Obtain ELB data
    elb_data = get_elb_data(load_balancer_name, region)
    # validate that an existing ALB with same name does not exist
    if alb_exist(load_balancer_name, region):
        print('An Application Load Balancer currently exists with the name {} in {}'.format(
            load_balancer_name, region))
        sys.exit(1)
    # # validate known failure scenarios
    if passed_hardfailure_detector(elb_data):
        if passed_softfailure_detector(elb_data):
            # quit early for dry run operation
            if args.dry_run:
                print(
                    'Your load balancer configuration is supported by this migration utility')
                sys.exit(0)
            alb_data = get_alb_data(elb_data, region, load_balancer_name)
            alb_arn = create_alb(alb_data)
            target_group_arns = create_target_groups(alb_data)
            # print 'alb data', alb_data
            create_listeners(alb_arn, alb_data, target_group_arns)
            load_attributes(alb_data, alb_arn)
            target_group_attributes(alb_data, alb_arn)
            add_tags(alb_data, alb_arn, target_group_arns)
            if args.register_targets:
                register_backends(target_group_arns, alb_data)
            print("Your Application Load Balancer is ready!")
            print("Application Load Balancer ARN:")
            print(alb_arn)
            print("Target group ARNs:")
            for target_group in target_group_arns:
                print(target_group['arn'])
            print("Considerations:")
            print("1. If your Classic load balancer is attached to an Auto Scaling group, attach the target groups to the Auto Scaling group.")
            print("2. All HTTPS listeners use the predefined security policy.")
            print(
                "3. To use Amazon EC2 Container Service (Amazon ECS), register your containers as targets.")
            return
    else:
        return 1

if __name__ == '__main__':
    main()
