import base64
import boto3


def lambda_handler(event, context):
	print '\n==event=='
	print event

	response = {
		"statusCode": 200,
		"statusDescription": "200 OK",
		"isBase64Encoded": False,
		"headers": {
			"Content-Type": "text/html;"
		}
	}
	encoded_data = event['body']
	S3KEY = event['queryStringParameters']['objectname']
	BUCKET_NAME = event['queryStringParameters']['bucketname']
	LOCALFILE = "/tmp/"+ S3KEY
	decoded_data = encoded_data.decode('base64')

	imageFile = open(LOCALFILE, "w")
	imageFile.write(decoded_data)
	imageFile.close()

	if event['headers']['user-agent']=='ELB-HealthChecker/2.0':
		print "This is a Health Check Request"
		response['body'] = 'Response to Health Check Request'
		return response
	if event['httpMethod']=='GET':
		response['body'] = 'Reponse to a GET request'
		return response
	if event['httpMethod']=='POST':
		s3 = boto3.resource('s3')
		try:
			s3.meta.client.upload_file(LOCALFILE, BUCKET_NAME, S3KEY)
			response['body'] = "Upload to S3 -- {} successfully".format(BUCKET_NAME)
			return response
		except Exception as e:
			print e
			response['body'] = "Failed to upload to S3 -- {}".format(BUCKET_NAME)
			return response

	data = "Default Response"
	response['body'] = data
	return response


