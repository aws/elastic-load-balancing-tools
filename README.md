## Classic load balancer to Application load balancer copy utility
 
### Overview:
Customers can utilize this tool to copy the configuration of their existing Classic load balancer to create a new Application load balancer with the same configuration. Customers can also choose to register their existing backend EC2 instances with the new Application load balancer.
 
### Usage:
```
copy_classic_load_balancer.py
--name <value>
--region <value>
[--debug <value>]
[--register-targets]
[--dry-run]
```

Example 1: Test whether the load balancer configuration is supported
```
copy_classic_load_balancer.py --name my-load-balancer –region us-west-2 --dry-run
```

Example 2: Create an Application load balancer based on the specified Classic load balancer but do not register the instances as targets
```
copy_classic_load_balancer.py --name my-load-balancer –region us-west-2
```

Example 3: Create an Application load balancer based on the specified Classic load balancer and register the instances as targets
```
copy_classic_load_balancer.py --name my-load-balancer –region us-west-2 --register-targets
```
 
### Unsupported Configurations:
1. Classic load balancer has TCP or SSL listeners
2. Classic load balancer is in EC2-Classic
3. Classic load balancer has only one enabled subnet
4. Classic load balancer has TCP or SSL health check configured
5. Classic load balancer has an unsupported policy (please note that this utility does not currently support sticky sessions)
6. Classic load balancer has more than 50 unique backend ports
7. Classic load balancer has more than 10 listeners
 
### Addition considerations and best practices:
1. If you are utilizing Auto Scaling groups (ASG) you will need to register the ASG with the respective target groups.
2. All HTTPS listeners created will be utilizing the AWS pre-defined cipher security policy. Please modify the HTTPS listener configurations if you need to use custom policies.
3. If you are utilizing Amazon EC2 Container Service (ECS) you will need to associate your ECS service with the new Application load balancer.
4. We recommend testing your application on the Application load balancer before migrating your traffic using DNS.
 
For more information please see the Application load balancer Documentation: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/introduction.html
 
### License
The Classic load balancer to Application load balancer copy utility is licensed under the Apache 2.0 License.


