import time
import json
import os

TEMPLATE = './whatismyip_template.html'


def lambda_handler(event, context):
	print '==event=='
	print event
	with open(TEMPLATE, 'rw') as template:
		template_html = template.read()

	response = {
		"statusCode": 200,
		"headers": {
			"Content-Type": "text/html;"
		},
		"isBase64Encoded": False
	}
	if event['headers']['user-agent'] == 'ELB-HealthChecker/2.0':
		print('HealthCheck Request')
		data = 'Response to HealthCheck'
		response['body'] = data
		return response

	sourceip_list = event['headers']['x-forwarded-for'].split(',')
	if sourceip_list:
		sourceip = str(sourceip_list[0])
		data = "<h3>Your IP is {}</h3>".format(sourceip)
		if event['queryStringParameters'] == {"output":"text"}:
			response['body']=sourceip
			return response
		if event['queryStringParameters'] == {"output":"json"}:
			response['body'] = json.dumps({"Source IP":sourceip})
			return response
	else:
		data = '<h3>No source IP found</h3>'
		response_html = template_html.replace("<!--whatismyip-->", data)
		return response_html


	print type(template_html)
	response_html = template_html.replace("<!--whatismyip-->", data)

	response['body'] = response_html
	print response
	return response


