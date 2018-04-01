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
import sys
import botocore
import csv


# Classic Load Balancer Console Link utility
#  version 1.0.0 2017
# Authors: Long Ren

# This script help create a spreadsheet of Classic Load Balancers' AWS console URL link along with other attributes
#such as 'Name', 'DNSName', 'Scheme', 'HostedZoneID', 'CreatedTime',
# 'VPCId', 'AvailabilityZones', 'EC2Platform', 'Subnets', 'SecurityGroup'


# With no parameters or configuration, boto3 looks for access keys here:
#
#    1. Environment variables (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)
#    2. Credentials file (~/.aws/credentials or
#         C:\Users\USER_NAME\.aws\credentials)
#    3. AWS IAM role for Amazon EC2 instance
#       (http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/iam-roles-for-amazon-ec2.html)

# Usage:
# classic_load_balancer_console_link.py
# --region <value>
# --format <value>
# [--debug <value>]

VERSION = '1.0.0'
CONSOLE_PREFIX = 'https://console.aws.amazon.com/ec2/v2/home?region='

# raw_input is now called input in python3, this allows backward compatability
try:
    input = raw_input
except NameError:
    pass

#Log will be stored in CLBConsoleLink.log file in the same directory as this utility script
logger = logging.getLogger()
logger.setLevel(logging.INFO)
formatter = logging.Formatter('%(asctime)s %(levelname)s %(message)s')
file_handler = logging.FileHandler('CLBConsoleLink.log')
file_handler.setFormatter(formatter)
file_handler.setLevel(logging.DEBUG)

stream_handler = logging.StreamHandler()
stream_handler.setFormatter(formatter)
stream_handler.setLevel(logging.ERROR)

logger.addHandler(file_handler)
logger.addHandler(stream_handler)



def get_elb_data(region):
    '''
    Describe the Classic Load Balancer and retrieve attributes
    '''
    if debug:
        logger.debug("Getting existing Classic Load Balancer data")
    elbc = boto3.client('elb', region_name=region)
    # Describes the specified Classic Load Balancer.
    try:
        paginator = elbc.get_paginator('describe_load_balancers')
    except botocore.exceptions.ClientError as e:
        logger.error(e.response['Error']['Message'])
    elb_data = []
    for describe_load_balancers in paginator.paginate():
    # Render a dictionary that contains the Classic Load Balancer attributes
        for lb in describe_load_balancers['LoadBalancerDescriptions']:
            elb_item = {}
            elb_item['DNSName']= lb['DNSName']
            elb_item['Scheme'] = lb['Scheme']
            elb_item['HostedZoneID'] = lb['CanonicalHostedZoneNameID']
            elb_item['Name'] = lb['LoadBalancerName']
            elb_item['ConsoleLink'] = CONSOLE_PREFIX + str(region) + '#LoadBalancers:loadBalancerName=' + lb['LoadBalancerName']
            elb_item['CreatedTime'] = lb['CreatedTime']
            elb_item['AvailabilityZones'] = lb['AvailabilityZones']
            elb_item['BackendInstances'] = lb['Instances']
            # Check if a Classic Load Balancer is in EC2-Classic or EC2-VPC
            if not lb['Subnets']:
                elb_item['EC2Platform'] = 'EC2-Classic'
                elb_item['Subnets'] = None
                elb_item['SecurityGroup'] = lb['SourceSecurityGroup']['GroupName']
                elb_item['VPCId'] = None
            else:
                elb_item['EC2Platform'] = 'EC2-VPC'
                elb_item['Subnets'] = lb['Subnets']
                elb_item['SecurityGroup'] = lb['SecurityGroups']
                elb_item['VPCId'] = lb['VPCId']
            elb_data.append(elb_item)
    if debug:
        logger.debug("elb data:")
        logger.debug(elb_data)
    return elb_data



def get_csv(elb_data):
    '''
    Generate a CSV file with Classic Load Balancers' Attributes and ConsoleLink
    '''
    fileds = sorted(list(set(k for d in elb_data for k in d)))
    with open('CLBConsoleLink.csv', 'wb') as csv_file:
        writer = csv.writer(csv_file)
        writer.writerow(fileds)
        for lb in elb_data:
            writer.writerow([lb.get(col,None) for col in fileds])
            writer.writerow([lb.get(col, None) for col in fileds])

def get_html(elb_data):
    '''
    Generate a html file with Classic Load Balancers' Attributes and ConsoleLink
    '''
    html = """<html><body><title>Classic Load Balancer Console Link</title><table border="1"><tr>"""
    colunm_names = sorted(list(set(k for d in elb_data for k in d)))
    for colunm in colunm_names:
        html += "<th>{}</th>".format(colunm)
    html += "</tr><tr>"
    for lb in elb_data:
        for attribute in ([lb.get(col, None) for col in colunm_names]):
            if isinstance(attribute,str):
                if CONSOLE_PREFIX in attribute:
                    html+='''<td><a href="{}">{}</a></td>'''.format(attribute,attribute.split('=')[-1])
                else:
                    html += "<td>{}</td>".format(attribute)
    html = """<!DOCTYPE html><html><title>Classic Load Balancer Console Link</title><body><table border="1"><tr>"""
    column_names = sorted(list(set(k for d in elb_data for k in d)))
    for column in column_names:
        html += "<th>{}</th>".format(column)
    html += "</tr>"
    for lb in elb_data:
        html += "<tr>"
        for attribute in ([lb.get(col, None) for col in column_names]):
            if isinstance(attribute, str) and (CONSOLE_PREFIX in attribute):
                html+='''<td><a href="{}">{}</a></td>'''.format(attribute, attribute.split('=')[-1])
            else:
                html += "<td>{}</td>".format(attribute)
        html += "</tr>"
    html += "</table></body></html>"
    with open('CLBConsoleLink.html', 'wb') as html_file:
        html_file.write(html)



def main():
    '''
    Taking in args in main function
    '''
    parser = argparse.ArgumentParser(
        description='Create a Console Link Spreadsheet for '
                    'Classic Load Balancers', usage='%(prog)s --region --format ')
    parser.add_argument("--region", help="The region of the Classic Load Balancers "
                                         "that you want to describe",required=True)
    parser.add_argument("--format", help="The format of the output file that you "
                                         "want to retrieve. Current "
                                         "supported formats are CSV and HTML", required=True)
    parser.add_argument("--debug", help="debug mode", action='store_true')
    # if no options, print help
    if len(sys.argv[1:]) == 0:
        parser.print_help()
        parser.exit()
    args = parser.parse_args()
    region = args.region
    format = args.format.lower()
    if format != 'csv' and format != 'html':
        logger.error('Unsupported output format. The supported '
                     'formats are HTML and CSV')
        parser.print_help()
        parser.exit()
    global debug
    debug = args.debug
    global client
    session = botocore.session.get_session()
    session.user_agent_name = 'CLBConsoleLink/' + VERSION
    # Obtain Classic Load Balancer data
    elb_data = get_elb_data(region)
    if format == 'csv':
        get_csv(elb_data)
    if format == 'html':
        get_html(elb_data)
if __name__ == '__main__':
    main()


