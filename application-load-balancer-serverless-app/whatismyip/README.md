# ALB-Lambda-Target-WhatIsMyIP
 
A sample Lambda function template that works with Application Load Balancer. It returns a page with client's source IP address when it is triggered.

You can also use query string parameter to specify the output format. 

For example, you can get text output by using query string -- "?output=text":
```
curl -ivv "http(s)://<<ALB FQDN and path to your Lambda target>>?output=text
```
You can also get JSON by using query string -- "?output=json"
```
curl -ivv "http(s)://<<ALB FQDN and path to your Lambda target>>?output=json
```


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

## How does it look like
![](https://github.com/renlon/elastic-load-balancing-tools/blob/master/application-load-balancer-serverless-app/whatismyip/app.jpg)

## License

Apache License 2.0 (Apache-2.0)

Made with ❤️ by AWS Elastic Load Balancing. Available on the [AWS Serverless Application Repository](https://aws.amazon.com/serverless)