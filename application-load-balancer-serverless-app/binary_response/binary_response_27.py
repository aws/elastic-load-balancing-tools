import base64


def lambda_handler(event, context):
	print '\n==event=='
	print event

	response = {
		"statusCode": 200,
		"statusDescription": "200 OK",
		"isBase64Encoded": True,
		"headers": {
			"Content-Type": "image/png;"
		}
	}

	with open("./image1.png", "rb") as imageFile:
		raw_data = imageFile.read()
		encoded_data = base64.b64encode(raw_data)

	response['body']=encoded_data
	return response


