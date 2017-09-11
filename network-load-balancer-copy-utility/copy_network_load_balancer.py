#!/usr/bin/env python
"""
Classic Load Balancer to Network Load Balancer copy utility
version 1.0.0 2016
Authors: Long Ren, Dan Lindow, Max Clements, Tipu Qureshi

This script uses the configuration of the specified Classic Load Balancer
to create an Network Load Balancer in a "best effort" manner.
Optionally, it can register existing backend instances as targets.
Tags and health checks are replicated for each target group.

With no parameters or configuration, boto3 looks for access keys here:

    1. Environment variables (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)
    2. Credentials file (~/.aws/credentials or
        C:\Users\USER_NAME\.aws\credentials)
    3. AWS IAM role for Amazon EC2 instance
    (http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)

Usage:
    copy_classic_load_balancer.py
    --name <value>
    --region <value>
    [--debug <value>]
    [--register-targets]
    [--dry-run]

Copyright 2016. Amazon Web Services, Inc. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

# Import the SDK and required libraries
import argparse
import logging
import sys
import botocore
import boto3

VERSION = '1.0.0'
# raw_input is now called input in python3, this allows backward compatability
try:
    input = raw_input
except NameError:
    pass

# Log will be stored in CLBtoNLBcopy.log file in the same directory as this utility script
# logging.info("Start logging......")
logger = logging.getLogger()
logger.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s %(levelname)s %(message)s')
file_handler = logging.FileHandler('CLBtoNLBcopy.log')
file_handler.setFormatter(formatter)
file_handler.setLevel(logging.DEBUG)

stream_handler = logging.StreamHandler()
stream_handler.setFormatter(formatter)
stream_handler.setLevel(logging.ERROR)

logger.addHandler(file_handler)
logger.addHandler(stream_handler)


def nlb_exist(load_balancer_name):
    """
    Returns True if NLB name already exists, False if it does not
    """
    if debug:
        logger.debug('checking if NLB exists')
    try:
        unused_response = client.describe_load_balancers(Names=[load_balancer_name])
    except botocore.exceptions.ClientError as exception:
        if 'LoadBalancerNotFound' in exception.response['Error']['Code']:
            return False
    else:
        return True


def get_elb_data(elb_name, region):
    """
    Describe the Classic Load Balancer and retrieve attributes
    """
    if debug:
        logger.debug("Getting existing Classic Load Balancer data")
    elbc = boto3.client('elb', region_name=region)
    # Describes the specified Classic Load Balancer.
    try:
        describe_load_balancers = elbc.describe_load_balancers(
            LoadBalancerNames=[elb_name])
    except botocore.exceptions.ClientError as exception:
        if 'LoadBalancerNotFound' in exception.response['Error']['Code']:
            logger.error('Cannot find a Classic Load Balancer in region {} named {}'.format(
                region, elb_name))
        else:
            logger.debug(exception)
    # Describes the attributes for the specified Classic Load Balancer.
    describe_load_balancer_attributes = elbc.describe_load_balancer_attributes(
        LoadBalancerName=elb_name)

    # Describes the specified policies.
    describe_load_balancer_policies = elbc.describe_load_balancer_policies(
        LoadBalancerName=elb_name)

    # Describes the tags associated with the specified Classic Load Balancers.

    describe_tags = elbc.describe_tags(
        LoadBalancerNames=[elb_name])

    # Render a dictionary that contains the Classic Load Balancer attributes
    elb_data = {}
    elb_data.update(describe_load_balancers)
    elb_data.update(describe_load_balancer_attributes)
    elb_data.update(describe_load_balancer_policies)
    elb_data.update(describe_tags)
    if debug:
        logger.debug("elb data:")
        logger.debug(elb_data)
    return elb_data


"""Define hard failure cases"""


def passed_hardfailure_detector(elb_data):
    """
    Checks for hard failures
    """
    if debug:
        logger.debug("Checking hard failure detector")

    # 1. Verify the Classic Load Balancer does not have HTTP, HTTPS or SSL
    # listeners
    for listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
        if listener['Listener']['Protocol'] in ['HTTP', 'HTTPS', 'SSL']:
            logger.error(
                "Error: HTTP, HTTPS and SSL listeners are not supported on Network Load Balancer.")
            return False

    # 2. Verify the Classic Load Balancer is not in EC2-Classic
    if 'VPCId' not in elb_data['LoadBalancerDescriptions'][0]:
        logger.error("Error: The Classic Load Balancer is in EC2-Classic instead of a VPC.\
A VPC is required for an Network Load Balancer.")
        return False

    # 3. Verify the Classic Load Balancer has more than 350 seconds idle
    # timeout
    if elb_data['LoadBalancerAttributes']['ConnectionSettings']['IdleTimeout'] > 350:
        logger.error(
            "Error: The idle timeout on Classic Load Balancer is larger than 350 seconds.")
        return False

    # 4. Verify unique backend ports is less than 200
    if len(elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']) > 200:
        backend_ports = []
        for listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
            if listener['Listener']['InstancePort'] not in backend_ports:
                backend_ports.append(listener['Listener']['InstancePort'])
        if len(backend_ports) >= 200:
            logger.error("Error: The number of unique backend ports exceeds 200. \
            The default limit for target groups is 200.")
            return False
    # 5. Verify that the number of listeners is less than the default
    if len(elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']) > 10:
        logger.error("Error: The number of listeners exceeds the default \
        limit for an Network Load Balancer.")
        return False
    return True


def passed_softfailure_detector(elb_data):
    """
    Checks for any soft failures
    """
    if debug:
        logger.debug("Checking soft failure detector")
    # 1. Check for AWS reserved tag
    if len(elb_data['TagDescriptions']):
        # This creates a copy of the list and allows us to iterate over the
        # copy so we can modify the original
        for tag in elb_data['TagDescriptions'][0]['Tags'][:]:
            if tag['Key'].startswith('aws:'):
                print("AWS reserved tag is in use. The aws: prefix in your tag names or \
values because it is reserved for AWS use -- \
http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_Tags.html#tag-restrictions")
                print('Tag key: {}'.format(tag['Key']))
                answer = input(
                    "Do you want to proceed without AWS reserved tag? y/n ")
                if answer.lower() == 'y':
                    elb_data['TagDescriptions'][0]['Tags'].remove(tag)
                    pass
                else:
                    print(
                        "We will not clone the Classic Load Balancer to an Network Load Balancer. Stopping script.")
                    return [False, None]

    # 2. If SSL health check is detected, prompt to check if continue.
    if 'SSL' in elb_data['LoadBalancerDescriptions'][0]['HealthCheck']['Target']:
        sslhc_check = input('SSL health check is not supported for an Network Load Balancer. '
                            'Continue with HTTPS health check? (y/n) ')
        if sslhc_check.lower() == 'y':
            # prompt for health check path
            ssl_hc_path = input('Please specify the path of HTTPS health check. Please note '
                                'that Health check path must begin with a ""/"" character. '
                                '(for example -- /index.html) ')
            if not ssl_hc_path.startswith('/'):
                logger.error('Health check path must begin with a ''/'' character and \
                can only contain printable ASCII characters, without spaces')
                sys.exit(1)
            return [True, ssl_hc_path]
        else:
            logger.error(
                "Error: The Classic Load Balancer uses SSL health checks.")
            logger.error(
                "We will not clone the Classic Load Balancer to an Network Load Balancer. Stopping script.")
            return [False, None]
    return [True, None]


def get_nlb_data(elb_data, region, load_balancer_name, ssl_hc_path):
    """
    Render a dictionary which contains Network Load Balancer attributes
    """
    if debug:
        logger.debug("Building the Network Load Balancer data structure")
    # this is used for building the load balancer spec
    nlb_data = {'VpcId': elb_data['LoadBalancerDescriptions'][0]['VPCId'], 'Region': region,
                'Nlb_name': elb_data['LoadBalancerDescriptions'][0]['LoadBalancerName'],
                'Subnets': elb_data['LoadBalancerDescriptions'][0]['Subnets'],
                'Security_groups': elb_data['LoadBalancerDescriptions'][0]['SecurityGroups'],
                'Scheme': elb_data['LoadBalancerDescriptions'][0]['Scheme'],
                'Tags': elb_data['TagDescriptions'][0]['Tags'],
                'listeners': [],
                'Type': 'network',
                'target_group_attributes': [],
                'target_group_arns': []}

    # this is used for building the listeners specs
    for elb_listener in elb_data['LoadBalancerDescriptions'][0]['ListenerDescriptions']:
        listener = {'Protocol': elb_listener['Listener']['Protocol'],
                    'Port': elb_listener['Listener']['LoadBalancerPort'],
                    'TargetGroup_Port': elb_listener['Listener']['InstancePort'],
                    'TargetGroup_Protocol': elb_listener['Listener']['InstanceProtocol']}
        targetgroup_attribute = {
            'dereg_timeout_seconds_delay': str(elb_data['LoadBalancerAttributes']['ConnectionDraining']['Timeout']),
            'TargetGroup_Port': elb_listener['Listener']['InstancePort']
        }
        nlb_data['listeners'].append(listener)
        nlb_data['target_group_attributes'].append(targetgroup_attribute)

    # this is used for building the target groups
    nlb_data['target_groups'] = []
    target_group = {}
    # Get health check target
    hc_target = elb_data['LoadBalancerDescriptions'][
        0]['HealthCheck']['Target']
    # Set health check interval
    if elb_data['LoadBalancerDescriptions'][0]['HealthCheck']['Interval'] < 15:
        print("The minimal supported health check interval is 10. Setting it to 10 seconds")
        target_group['HealthCheckIntervalSeconds'] = 10
    else:
        print("The health check internal is set to 30 seconds")
        target_group['HealthCheckIntervalSeconds'] = 30
    # Set healthy and unhealthy threshold to the same value which is the
    # healthy threshold of Classic Load Balancer
    target_group['HealthyThresholdCount'] = elb_data['LoadBalancerDescriptions'][0]['HealthCheck'][
        'HealthyThreshold']
    target_group['UnhealthyThresholdCount'] = elb_data['LoadBalancerDescriptions'][0]['HealthCheck'][
        'HealthyThreshold']

    # Set VPC ID
    target_group['VpcId'] = elb_data[
        'LoadBalancerDescriptions'][0]['VPCId']
    # Set health check protocol
    target_group['HealthCheckProtocol'] = hc_target.split(':')[0]
    # If health check protocol is TCP
    if hc_target.split(':')[0] == "TCP":
        target_group['HealthCheckPort'] = hc_target.split(':')[1]
    # If health check protocol is HTTP or HTTPs
    elif hc_target.split(':')[0] == "SSL":
        target_group['HealthCheckProtocol'] = "HTTPS"
        target_group['HealthCheckPort'] = hc_target.split(':')[1]
        target_group['HealthCheckPath'] = ssl_hc_path
    else:
        target_group['HealthCheckPort'] = hc_target.split(':')[1].split('/')[0]
        target_group['HealthCheckPath'] = '/' + hc_target.split('/', 1)[1]

    for listener in nlb_data['listeners']:
        target_group['Protocol'] = listener['TargetGroup_Protocol']
        target_group['Port'] = listener['TargetGroup_Port']
        # target group name comes from the first 18 character of the Classic Load Balancer name, \
        # "-nlb-tg-" and target group port.
        target_group['Name'] = load_balancer_name[: 18] + "-nlb-tg-" + \
            str(listener['TargetGroup_Port'])
        # Only append unique Target Group
        if target_group not in nlb_data['target_groups']:
            nlb_data['target_groups'].append(target_group.copy())
    # Get registered backend instances
    nlb_data['instanceIds'] = []
    for instance in elb_data['LoadBalancerDescriptions'][0]['Instances']:
        nlb_data['instanceIds'].append(instance['InstanceId'])
    if debug:
        logger.debug("nlb_data:")
        logger.debug(nlb_data)
    return nlb_data


def create_nlb(nlb_data, eipalloc):
    """
    Create the NLB
    """
    if debug:
        logger.debug("Creating the Network Load Balancer")
        # If EIPs are not specified
    if eipalloc is None:
        request = {'Name': nlb_data['Nlb_name'], 'Subnets': nlb_data['Subnets'],
                   'Scheme': nlb_data['Scheme'], 'Type': nlb_data['Type']}
    else:
        # If the number of subnets and EIPs matches
        if len(nlb_data['Subnets']) == len(eipalloc):
            subnetmappings = []
            for i, j in zip(nlb_data['Subnets'], eipalloc):
                subnetmappings.append({'SubnetId': i, 'AllocationId': j})
            request = {'Name': nlb_data['Nlb_name'], 'SubnetMappings': subnetmappings,
                       'Scheme': nlb_data['Scheme'], 'Type': nlb_data['Type']}
        else:
            logger.error("The number of EIPs and Subnets does not match")
            sys.exit(1)

    if len(nlb_data['Tags']) >= 1:
        request['Tags'] = nlb_data['Tags']
    try:
        response = client.create_load_balancer(**request)
    except botocore.exceptions.ParamValidationError as exception:
        logger.error("Failed to create Network Load Balancer")
        logger.error(exception)
        sys.exit(1)
    if debug:
        logger.debug("Create Network Load Balancer response:")
        logger.debug(response)
    return response['LoadBalancers'][0]['LoadBalancerArn']


def create_target_groups(nlb_data):
    """
    Given NLB data, creates the target group(s)
    """
    if debug:
        logger.debug("Creating the target groups")
    for target_group in nlb_data['target_groups']:
        response = client.create_target_group(**target_group)
        if debug:
            logger.debug("Create target group %s response: " %
                         (target_group['Name']))
            logger.debug(response)
        # we store some meta data about each target group, this is used binding
        # the listener to the TG
        for target_group in response['TargetGroups']:
            target_group_arn = {'arn': target_group['TargetGroupArn'],
                                'backend_port': target_group['Port']}
            if target_group_arn not in nlb_data['target_group_arns']:
                nlb_data['target_group_arns'].append(target_group_arn.copy())
    return nlb_data['target_group_arns']


def create_listeners(nlb_arn, nlb_data, target_groups):
    """"
    Given an NLB ARN, NLB data and target groups
    Create the listeners
    Listeners are in nlb_data
    """
    if debug:
        logger.debug("Getting listeners")
    for listener in nlb_data['listeners']:
        # this is how we know which listener gets bound to which target group
        for target_group in target_groups:
            if target_group['backend_port'] == listener['TargetGroup_Port']:
                listener['DefaultActions'] = [
                    {'TargetGroupArn': target_group['arn'], 'Type': 'forward'}]
                # Remove these, else the call will fail.
                listener.pop('TargetGroup_Protocol', None)
                listener.pop('TargetGroup_Port', None)
                try:
                    response = client.create_listener(
                        LoadBalancerArn=nlb_arn, **listener)
                except botocore.exceptions.ParamValidationError as exception:
                    logger.error(
                        "Failed to create Network Load Balancer Listeners")
                    logger.error(exception)
                    sys.exit(1)
                if debug:
                    logger.debug("Create listener(%s) response: " %
                                 (listener['Port']))
                    logger.debug(response)
                break


def target_group_attributes(nlb_data):
    """
    Configure target group's attributes from nlb_data
    """
    # Create a configuration dict of Target Group attributes
    attributes = {}
    for target_group_attribute in nlb_data['target_group_attributes']:
        for tg_arn in nlb_data['target_group_arns']:
            attributes[tg_arn['arn']] = [
                {'Key': 'deregistration_delay.timeout_seconds',
                 'Value': target_group_attribute['dereg_timeout_seconds_delay']}]
    # Configure Target Group attributes
    for arn in attributes:
        try:
            response = client.modify_target_group_attributes(
                TargetGroupArn=arn, Attributes=attributes[arn])
        except botocore.exceptions.ParamValidationError as exception:
            logger.error(
                "Failed to configure Network Load Balancer target group attributes")
            logger.error(exception)
            sys.exit(1)
        if debug:
            logger.debug("Modify target group attributes response: ")
            logger.debug(response)


def add_tags(nlb_data, nlb_arn, target_groups):
    """
    Add tag to Network Load Balancer and Target Group
    """
    if debug:
        logger.debug("Tagging the Network Load Balancer and target groups")
    if len(nlb_data['Tags']) >= 1:
        for target_group in target_groups:
            try:
                client.add_tags(ResourceArns=[target_group[
                                'arn']], Tags=nlb_data['Tags'])
            except botocore.exceptions.ParamValidationError as exception:
                logger.error("Failed to add target group tags")
                logger.error(exception)
                sys.exit(1)
        try:
            client.add_tags(ResourceArns=[nlb_arn], Tags=nlb_data['Tags'])
        except botocore.exceptions.ParamValidationError as exception:
            logger.error("Failed to add Network Load Balancer tags")
            logger.error(exception)
            sys.exit(1)


def register_backends(target_groups, nlb_data):
    """
    Register back-ends to the NLB
    Given target_groups and nlb_data
    add the instances from nlb_data to the target_groups given
    """
    if debug:
        logger.debug("Registering targets with the Network Load Balancer")
    if len(nlb_data['instanceIds']) >= 1:
        for target_group in target_groups:
            targets = []
            for instance in nlb_data['instanceIds']:
                target = {'Id': instance}
                targets.append(target)
            try:
                response = client.register_targets(
                    TargetGroupArn=target_group['arn'], Targets=targets)
            except botocore.exceptions.ParamValidationError as exception:
                logger.error("Failed to register targets")
                logger.error(exception)
                sys.exit(1)
            if debug:
                logger.debug("Register targets response:")
                logger.debug(response)


# # Taking in args in main function
def main():
    parser = argparse.ArgumentParser(
        description='Create an Network Load Balancer from a '
                    'Classic Load Balancer', usage='%(prog)s --name <elb name> --region')
    parser.add_argument(
        "--name", help="The name of the Classic Load Balancer", required=True)
    parser.add_argument("--region", help="The region of the Classic Load Balancer "
                                         "(will also be used for the Network Load Balancer)",
                        required=True)
    parser.add_argument("--debug", help="debug mode", action='store_true')
    parser.add_argument("--register-targets", help="Register the backend instances of "
                                                   "the Classic Load Balancer with the Network Load Balancer",
                        action='store_true')
    parser.add_argument("--dry-run", help="Validate that the current Classic Load Balancer configuration is compatible "
                                          "with Network Load Balancers, but do not perform create operations",
                        action='store_true')
    parser.add_argument("--allocationid", nargs='+', metavar='', help="Allocation ID for the VPC Elastic \
    IP address you want to associate with the Network Load Balancer")
    # if no options, print help
    if len(sys.argv[1:]) == 0:
        parser.print_help()
        parser.exit()
    args = parser.parse_args()
    load_balancer_name = args.name
    region = args.region
    eipalloc = args.allocationid
    global debug
    debug = args.debug
    global client
    session = botocore.session.get_session()
    session.user_agent_name = 'CopyClassicToNetwork/' + VERSION
    client = session.create_client('elbv2', region_name=region)
    ec2_client = session.create_client('ec2', region_name=region)

    # If input gets allocation ID. Verify allocation ID
    if eipalloc is not None:
        try:
            ip_addresses = ec2_client.describe_addresses(
                AllocationIds=eipalloc)
        except botocore.exceptions.ClientError as exception:
            # Verify if the input allocation IDs exist
            if 'InvalidAllocationID.NotFound' in exception.response['Error']['Code']:
                logger.error(exception.response['Error']['Message'])
        # Verify if the input allocation IDs are not in use
        for ip_address in ip_addresses['Addresses']:
            if 'AssociationId' in ip_address:
                logger.error('The EIPs {} ({}) are already in use'.format(
                    ip_address['PublicIp'], ip_address['AllocationId']))
                eipalloc.remove(ip_address['AllocationId'])
                sys.exit(0)
            if debug:
                logger.debug('EIP is valid and not in use. ')
    else:
        logger.debug(
            'No EIPs are provided. Auto-assign Public IP will be used')
    # validate that an existing NLB with same name does not exist
    if nlb_exist(load_balancer_name):
        logger.error('You already have a load balancer with the name {} in {}'.format(
            load_balancer_name, region))
        sys.exit(1)
    # Obtain Classic Load Balancer data
    elb_data = get_elb_data(load_balancer_name, region)
    # validate known failure scenarios
    if passed_hardfailure_detector(elb_data):
        logger.debug('hardfailure pass')
        softfailurecheck_result = passed_softfailure_detector(elb_data)
        if softfailurecheck_result[0]:
            ssl_hc_path = softfailurecheck_result[1]
            # quit early for dry run operation
            nlb_data = get_nlb_data(
                elb_data, region, load_balancer_name, ssl_hc_path)
            if args.dry_run:
                print ("Your load balancer configuration is supported by this migration utility.")
                print ("Your can find your Network Load Balancer's meta data in the utility log.")
                logger.debug(
                    'Pass both hard failure check and soft failure check.')
                logger.info(nlb_data)
                sys.exit(0)
            nlb_arn = create_nlb(nlb_data, eipalloc)
            target_group_arns = create_target_groups(nlb_data)
            create_listeners(nlb_arn, nlb_data, target_group_arns)
            target_group_attributes(nlb_data)
            add_tags(nlb_data, nlb_arn, target_group_arns)
            if args.register_targets:
                register_backends(target_group_arns, nlb_data)
            print("Your Network Load Balancer is ready!")
            print("Network Load Balancer ARN:")
            print(nlb_arn)
            print("Target group ARNs:")
            for target_group in target_group_arns:
                print(target_group['arn'])
            print("Considerations:")
            print("1. If your Classic Load Balancer is attached to an Auto Scaling group, "
                  "attach the target groups to the Auto Scaling group.")
            print(
                "2. To use Amazon EC2 Container Service (Amazon ECS), register your containers as targets.")
        else:
            logger.error("Soft failure check did not pass")
            sys.exit(1)
    else:
        logger.error("Hard failure check did not pass")
        sys.exit(1)


if __name__ == '__main__':
    main()
