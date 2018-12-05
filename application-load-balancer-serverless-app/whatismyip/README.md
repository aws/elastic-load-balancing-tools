# ALB-Lambda-Target-WhatIsMyIP
 
A sample Lambda function template that works with Application Load Balancer. It returns a page with client's source IP address when it is triggered.

[More demo of Lambda as target on Application Load Balancer](https://exampleloadbalancer.com/lambda_demo.html)
## TO DEPLOY
```
aws cloudformation package --template-file template.yaml --output-template-file serverless-output.yaml --s3-bucket <<<YOUR BUCKET NAME>>>
aws cloudformation deploy --template-file serverless-output.yaml --stack-name <<<YOUR STACK NAME>>> --capabilities CAPABILITY_IAM
```

## How does it look like
![](https://github.com/renlon/elastic-load-balancing-tools/blob/master/application-load-balancer-serverless-app/whatismyip/app.jpg)