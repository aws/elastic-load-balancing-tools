# ALB-Lambda-Target-HelloWorld
 
A sample Lambda function template that works with Application Load Balancer. It returns plain text "Hello World from Lambda" when it is triggered.

## TO DEPLOY
```
aws cloudformation package --template-file template.yaml --output-template-file serverless-output.yaml --s3-bucket <<<YOUR BUCKET NAME>>>
aws cloudformation deploy --template-file serverless-output.yaml --stack-name ALB-Lambda-Target-HelloWorld --capabilities CAPABILITY_IAM