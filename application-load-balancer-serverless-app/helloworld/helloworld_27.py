def lambda_handler(event, context):
	print '\n==event=='
	print event

	response = {
		    "statusCode": 200,
		    "statusDescription": "HTTP OK",
		    "headers": {
		        "Content-Type": "text/html; charset=utf-8"
		    },
			"isBase64Encoded": False
		}

	data = "Hello World from Lambda\n"
	response['body'] = data
	print '==Response==\n'
	print response
	return response

