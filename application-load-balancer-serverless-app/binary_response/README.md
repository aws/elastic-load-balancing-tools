# ALB-Lambda-Target-HelloWorld
 
A sample Lambda function template that works with Application Load Balancer. It reads a local .png image file, encodes the image data through base64, put the data into an HTTP response and sends it to the client. 

[More demo of Lambda as target on Application Load Balancer](https://exampleloadbalancer.com/lambda_demo.html)
## TO DEPLOY
```
aws cloudformation package --template-file template.yaml --output-template-file serverless-output.yaml --s3-bucket <<<YOUR BUCKET NAME>>>
aws cloudformation deploy --template-file serverless-output.yaml --stack-name <<<YOUR STACK NAME>>> --capabilities CAPABILITY_IAM
```
## License

Apache License 2.0 (Apache-2.0)

Made with ❤️ by longren. Available on the [AWS Serverless Application Repository](https://aws.amazon.com/serverless)