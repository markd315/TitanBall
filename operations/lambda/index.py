import os
import json
import datetime
import urllib.request
import boto3

# AWS Clients
ecs = boto3.client('ecs')
ec2 = boto3.client('ec2')
cloudfront = boto3.client('cloudfront')

def handler(event, context):
    print("Received event:", json.dumps(event))
    
    # Check trigger type
    if "RequestType" in event:
        # Triggered as a CloudFormation Custom Resource
        return handle_cfn_router(event, context)
    elif event.get("source") == "aws.events":
        # Triggered by EventBridge Scheduler (Reaper mode)
        return handle_reap(event, context)
    else:
        # Triggered by client HTTP request via Function URL (Warming mode)
        return handle_warm(event, context)

def handle_warm(event, context):
    deployment_type = os.environ.get("DEPLOYMENT_TYPE", "ECS")
    expiration_hours = int(os.environ.get("EXPIRATION_HOURS", "2"))
    
    # Calculate new expiration time
    now = datetime.datetime.now(datetime.timezone.utc)
    expiration_time = now + datetime.timedelta(hours=expiration_hours)
    expiration_str = expiration_time.isoformat()
    
    status = "unknown"
    message = ""
    
    try:
        if deployment_type == "ECS":
            cluster = os.environ.get("ECS_CLUSTER")
            service = os.environ.get("ECS_SERVICE")
            
            # Get current service status
            desc = ecs.describe_services(cluster=cluster, services=[service])
            if not desc['services']:
                raise Exception(f"ECS Service {service} not found in cluster {cluster}")
            
            svc = desc['services'][0]
            service_arn = svc['serviceArn']
            desired_count = svc['desiredCount']
            
            # Start/warm service if not running
            if desired_count != 1:
                print(f"Scaling ECS Service {service} to 1")
                ecs.update_service(cluster=cluster, service=service, desiredCount=1)
                status = "starting"
                message = "ECS service scaling to 1 initiated."
            else:
                status = "running"
                message = "ECS service is already running."
            
            # Update/set ExpirationTime tag
            print(f"Updating ECS Service tags with ExpirationTime: {expiration_str}")
            ecs.tag_resource(
                resourceArn=service_arn,
                tags=[{'key': 'ExpirationTime', 'value': expiration_str}]
            )
            
        elif deployment_type == "EC2":
            instance_id = os.environ.get("EC2_INSTANCE_ID")
            
            # Get current instance status
            desc = ec2.describe_instances(InstanceIds=[instance_id])
            instance = desc['Reservations'][0]['Instances'][0]
            state = instance['State']['Name']
            
            # Start instance if stopped
            if state in ["stopped", "stopping"]:
                print(f"Starting EC2 Instance {instance_id}")
                ec2.start_instances(InstanceIds=[instance_id])
                status = "starting"
                message = "EC2 instance start initiated."
            elif state == "running":
                status = "running"
                message = "EC2 instance is already running."
            else:
                status = state
                message = f"EC2 instance is in {state} state."
                
            # Update/set ExpirationTime tag
            print(f"Updating EC2 Instance tags with ExpirationTime: {expiration_str}")
            ec2.create_tags(
                Resources=[instance_id],
                Tags=[{'Key': 'ExpirationTime', 'Value': expiration_str}]
            )
        else:
            raise Exception(f"Unsupported DEPLOYMENT_TYPE: {deployment_type}")
            
    except Exception as e:
        print(f"Error warming server: {str(e)}")
        return {
            "statusCode": 500,
            "headers": {
                "Content-Type": "application/json",
                "Access-Control-Allow-Origin": "*"
            },
            "body": json.dumps({"status": "error", "error": str(e)})
        }
        
    return {
        "statusCode": 200,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*"
        },
        "body": json.dumps({
            "status": status,
            "message": message,
            "expirationTime": expiration_str
        })
    }

def handle_reap(event, context):
    deployment_type = os.environ.get("DEPLOYMENT_TYPE", "ECS")
    now = datetime.datetime.now(datetime.timezone.utc)
    
    print(f"Running Reaper scan. Current time (UTC): {now.isoformat()}")
    
    try:
        if deployment_type == "ECS":
            cluster = os.environ.get("ECS_CLUSTER")
            service = os.environ.get("ECS_SERVICE")
            
            # Describe service and load tags
            desc = ecs.describe_services(cluster=cluster, services=[service], include=['TAGS'])
            if not desc['services']:
                print(f"ECS Service {service} not found")
                return
            
            svc = desc['services'][0]
            desired_count = svc['desiredCount']
            
            if desired_count == 0:
                print("ECS Service is already scaled to 0. No action needed.")
                return
                
            # Search for ExpirationTime tag
            expiration_tag = next((t for t in svc.get('tags', []) if t['key'] == 'ExpirationTime'), None)
            if not expiration_tag:
                print("No ExpirationTime tag found on ECS Service. Skipping.")
                return
                
            expiration_time = datetime.datetime.fromisoformat(expiration_tag['value'])
            print(f"ECS Service ExpirationTime (UTC): {expiration_time.isoformat()}")
            
            if now > expiration_time:
                print("Expiration time reached! Scaling ECS Service desired count to 0.")
                ecs.update_service(cluster=cluster, service=service, desiredCount=0)
            else:
                remaining = (expiration_time - now).total_seconds()
                print(f"ECS Service has {remaining:.1f} seconds remaining.")
                
        elif deployment_type == "EC2":
            instance_id = os.environ.get("EC2_INSTANCE_ID")
            
            desc = ec2.describe_instances(InstanceIds=[instance_id])
            instance = desc['Reservations'][0]['Instances'][0]
            state = instance['State']['Name']
            
            if state not in ["running", "pending"]:
                print(f"EC2 Instance is in state: {state}. No action needed.")
                return
                
            # Search for ExpirationTime tag
            expiration_tag = next((t for t in instance.get('Tags', []) if t['Key'] == 'ExpirationTime'), None)
            if not expiration_tag:
                print("No ExpirationTime tag found on EC2 Instance. Skipping.")
                return
                
            expiration_time = datetime.datetime.fromisoformat(expiration_tag['Value'])
            print(f"EC2 Instance ExpirationTime (UTC): {expiration_time.isoformat()}")
            
            if now > expiration_time:
                print("Expiration time reached! Stopping EC2 Instance.")
                ec2.stop_instances(InstanceIds=[instance_id])
            else:
                remaining = (expiration_time - now).total_seconds()
                print(f"EC2 Instance has {remaining:.1f} seconds remaining.")
                
    except Exception as e:
        print(f"Error in Reaper scan: {str(e)}")

def handle_cfn_router(event, context):
    request_type = event["RequestType"]
    properties = event["ResourceProperties"]
    dist_id = properties.get("CloudFrontDistributionId")
    warm_lambda_url = properties.get("WarmLambdaUrl")
    
    if not dist_id or not warm_lambda_url:
        send_cfn_response(event, context, "FAILED", {"Message": "Missing required properties."})
        return
        
    # Extract domain name from Function URL (strip https:// and trailing /)
    domain_name = warm_lambda_url.replace("https://", "").replace("/", "")
    origin_id = "PilotLightLambdaUrlOrigin"
    
    print(f"Custom Resource: {request_type} for CFD {dist_id} pointing to {domain_name}")
    
    try:
        # 1. Fetch current CloudFront Distribution Config
        response = cloudfront.get_distribution_config(Id=dist_id)
        config = response['DistributionConfig']
        etag = response['ETag']
        
        updated = False
        
        if request_type in ["Create", "Update"]:
            # Ensure Origin exists
            origins_container = config.get('Origins', {})
            origins_items = origins_container.get('Items', [])
            
            new_origin = {
                'Id': origin_id,
                'DomainName': domain_name,
                'OriginPath': '',
                'CustomHeaders': {'Quantity': 0, 'Items': []},
                'CustomOriginConfig': {
                    'HTTPPort': 80,
                    'HTTPSPort': 443,
                    'OriginProtocolPolicy': 'https-only',
                    'OriginSslProtocols': {
                        'Quantity': 1,
                        'Items': ['TLSv1.2']
                    },
                    'OriginReadTimeout': 30,
                    'OriginKeepaliveTimeout': 5
                },
                'ConnectionAttempts': 3,
                'ConnectionTimeout': 10,
                'OriginShield': {'Enabled': False}
            }
            
            origin_idx = next((i for i, o in enumerate(origins_items) if o['Id'] == origin_id), -1)
            if origin_idx >= 0:
                if origins_items[origin_idx]['DomainName'] != domain_name:
                    origins_items[origin_idx] = new_origin
                    updated = True
            else:
                origins_items.append(new_origin)
                updated = True
                
            origins_container['Items'] = origins_items
            origins_container['Quantity'] = len(origins_items)
            config['Origins'] = origins_container
            
            # Ensure Cache Behavior exists
            behaviors_container = config.get('CacheBehaviors', {})
            behaviors_items = behaviors_container.get('Items', [])
            
            new_behavior = {
                'PathPattern': '/api/warm',
                'TargetOriginId': origin_id,
                'ViewerProtocolPolicy': 'redirect-to-https',
                'AllowedMethods': {
                    'Quantity': 7,
                    'Items': ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'OPTIONS', 'DELETE'],
                    'CachedMethods': {
                        'Quantity': 2,
                        'Items': ['GET', 'HEAD']
                    }
                },
                'SmoothStreaming': False,
                'Compress': True,
                'LambdaFunctionAssociations': {'Quantity': 0, 'Items': []},
                'FieldLevelEncryptionId': '',
                'CachePolicyId': '4135ea2d-6df8-44a3-9df3-4b5a84be39ad', # CachingDisabled
                'OriginRequestPolicyId': 'b689b0a8-53d0-40dd-86a5-5ec57a859476' # AllViewerExceptHostHeader
            }
            
            behavior_idx = next((i for i, b in enumerate(behaviors_items) if b['PathPattern'] == '/api/warm'), -1)
            if behavior_idx >= 0:
                if behaviors_items[behavior_idx]['TargetOriginId'] != origin_id:
                    behaviors_items[behavior_idx] = new_behavior
                    updated = True
            else:
                # Insert at the beginning so it evaluates before generic * patterns
                behaviors_items.insert(0, new_behavior)
                updated = True
                
            behaviors_container['Items'] = behaviors_items
            behaviors_container['Quantity'] = len(behaviors_items)
            config['CacheBehaviors'] = behaviors_container
            
        elif request_type == "Delete":
            # Remove Cache Behavior
            behaviors_container = config.get('CacheBehaviors', {})
            behaviors_items = behaviors_container.get('Items', [])
            original_len = len(behaviors_items)
            
            behaviors_items = [b for b in behaviors_items if b['PathPattern'] != '/api/warm']
            if len(behaviors_items) != original_len:
                updated = True
                
            behaviors_container['Items'] = behaviors_items
            behaviors_container['Quantity'] = len(behaviors_items)
            config['CacheBehaviors'] = behaviors_container
            
            # Remove Origin
            origins_container = config.get('Origins', {})
            origins_items = origins_container.get('Items', [])
            original_origins_len = len(origins_items)
            
            origins_items = [o for o in origins_items if o['Id'] != origin_id]
            if len(origins_items) != original_origins_len:
                updated = True
                
            origins_container['Items'] = origins_items
            origins_container['Quantity'] = len(origins_items)
            config['Origins'] = origins_container
            
        # 2. Update CloudFront if modified
        if updated:
            print("Config modified, updating CloudFront distribution...")
            cloudfront.update_distribution(Id=dist_id, DistributionConfig=config, IfMatch=etag)
            print("CloudFront update request sent successfully")
        else:
            print("No configuration changes needed")
            
        send_cfn_response(event, context, "SUCCESS", {"Message": "CloudFront distribution configuration completed."})
        
    except Exception as e:
        print(f"Error handling CloudFront configuration: {str(e)}")
        send_cfn_response(event, context, "FAILED", {"Message": f"Exception: {str(e)}"})

def send_cfn_response(event, context, response_status, response_data, physical_resource_id=None):
    response_body = json.dumps({
        "Status": response_status,
        "Reason": f"See details in CloudWatch Log Stream: {context.log_stream_name}",
        "PhysicalResourceId": physical_resource_id or context.log_stream_name or "CloudFrontRouterStaticId",
        "StackId": event["StackId"],
        "RequestId": event["RequestId"],
        "LogicalResourceId": event["LogicalResourceId"],
        "Data": response_data
    }).encode('utf-8')
    
    print("Sending response to CloudFormation:", response_body.decode('utf-8'))
    
    req = urllib.request.Request(
        event["ResponseURL"],
        data=response_body,
        headers={"content-type": "", "content-length": str(len(response_body))},
        method="PUT"
    )
    
    try:
        with urllib.request.urlopen(req) as f:
            print("CloudFormation response sent successfully, status code:", f.getcode())
    except Exception as e:
        print("Failed to send CloudFormation response:", str(e))
