rem Following statements are from the AWS Console for ECS.
cd .. && ^
aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin 785668202743.dkr.ecr.eu-central-1.amazonaws.com && ^
docker build -t traffic-guard-ecr-repo . && ^
docker tag traffic-guard-ecr-repo:latest 785668202743.dkr.ecr.eu-central-1.amazonaws.com/traffic-guard-ecr-repo:latest && ^
docker push 785668202743.dkr.ecr.eu-central-1.amazonaws.com/traffic-guard-ecr-repo:latest && ^
cd scripts
