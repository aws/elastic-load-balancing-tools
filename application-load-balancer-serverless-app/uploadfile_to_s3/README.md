# ALB-Lambda-Target-UploadFiletoS3
 
A sample Lambda function template that works with Application Load Balancer. You can upload a binary file (e.g. an image or video file) to your S3 bucket with a **POST** request to trigger this Lambd function through your Application Load Balancer. 


You need to use query string parameter to specify the S3 bucket , S3 Object Key (file name) that the Lambda function can use to upload the file as an object to S3.

For example, to upload an image file -- logo.png as test.png to the S3 bucket -- myBucket:

```
curl -ivv -X POST -H "Content-Type: image/png" -F "data=@logo.png" "http(s)://<<ALB FQDN and path to your Lambda target>>?objectname=test.png&bucketname=myBucket"
```


*Note: This template creates an IAM role that the Lambda function can assume to upload files to S3. Please adjust the IAM role for your own use case.*

[More demo of Lambda as target on Application Load Balancer](https://exampleloadbalancer.com/lambda_demo.html)
## TO DEPLOY
```
aws cloudformation package --template-file template.yaml --output-template-file serverless-output.yaml --s3-bucket <<<YOUR BUCKET NAME>>>
aws cloudformation deploy --template-file serverless-output.yaml --stack-name <<<YOUR STACK NAME>>> --capabilities CAPABILITY_IAM
```

##Register Lambda to your Application Load Balancer
Create a target group, which is used in request routing. If the request content matches a listener rule with an action to forward it to this target group, the load balancer invokes the registered Lambda function. 
To create a target group and register the Lambda function

1. Open the Amazon EC2 console at [https://console.aws.amazon.com/ec2/]((https://aws.amazon.com/serverless)).

2. On the navigation pane, under **LOAD BALANCING**, choose **Target Groups**.

3. Choose **Create target group**.

4. For **Target group name**, type a name for the target group.

5. For **Target type**, select **Lambda function**.

6. Register the Lambda function that is deployed earlier after you create the target group


## License

Apache License 2.0 (Apache-2.0)

Made with ❤️ by AWS Elastic Load Balancing. Available on the [AWS Serverless Application Repository](https://aws.amazon.com/serverless)