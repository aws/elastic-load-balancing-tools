## Classic Load Balancer to Network Load Balancer copy utility
 
### Overview:
Customers can utilize this tool to copy the configuration of their existing Classic Load Balancer to create a new Network Load Balancer with the same configuration. Customers can also choose to register their existing backend EC2 instances with the new Network Load Balancer.

Note: Please make sure that your botocore version number is higher than 1.7.6.

### Usage:
```
copy_classic_load_balancer.py
--name <value>
--region <value>
[--debug <value>]
[--register-targets]
[--dry-run]
```

Example 1: Test whether the Load Balancer configuration is supported
```
copy_classic_load_balancer.py --name my-load-balancer --region us-west-2 --dry-run
```

Example 2: Create an Network Load Balancer based on the specified Classic Load Balancer but do not register the instances as targets
```
copy_classic_load_balancer.py --name my-load-balancer --region us-west-2
```

Example 3: Create an Network Load Balancer based on the specified Classic Load Balancer and register the instances as targets
```
copy_classic_load_balancer.py --name my-load-balancer --region us-west-2 --register-targets
```
 
### Unsupported Configurations:
1. A Classic Load Balancer has HTTP, HTTPS or SSL listeners
2. A Classic Load Balancer is in EC2-Classic
3. A Classic Load Balancer has been configured with an idle timeout that is longer than 350 seconds.
4. A Classic Load Balancer has more than 200 unique backend ports
5. A Classic Load Balancer has more than 10 listeners


### Addition considerations and best practices:
1. You cannot assign Elastic IP addresses or change their allocation to a Network Load Balancer after the creation step has completed successfully.
2. Network Load Balancer only accept the same value for healthy and unhealthy threshold and this utility tool set this value to the healthy threshold of Classic Load Balancer.
3. Network Load Balancer only supports a 10 second, or a 30 second health check interval
4. This tool will only set the health check matching HttpCode to 200-399. If you want to further customize the HTTP response code that is considered as healthy you will need to change it via the AWS console or CLI after the Network Load Balancer is created with this tool.
5. If you are using an Auto Scaling Group, you will have to manually register the Auto Scaling group with the appropriate target groups.
6. If you are utilizing Amazon EC2 Container Service (ECS) you will need to configure your service to run behind your Network Load Balancer.
7. We recommend testing your application on the Network Load Balancer before migrating your traffic. Amazon Route 53 weighted resource record sets let you associate multiple resources with a single DNS name. Using these weighted resource record sets, you can gradually shift your traffic from your Classic Load Balancer to your new Network Load Balancer after testing is complete. For more information about weighted routing please see:
To learn how to create resource records in Route 53 please see: http://docs.aws.amazon.com/Route53/latest/DeveloperGuide/routing-policy.html#routing-policy-weighted
For more information please see the Network Load Balancer Documentation: https://docs.aws.amazon.com/elasticloadbalancing/latest/network/introduction.html
 
### License
The Classic Load Balancer to Network Load Balancer copy utility is licensed under the Apache 2.0 License: https://www.apache.org/licenses/LICENSE-2.0


