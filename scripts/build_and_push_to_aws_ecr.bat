cd .. && ^
aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin 469694857107.dkr.ecr.eu-central-1.amazonaws.com && ^
docker build -t traffic-guard-ecr-repo . && ^
docker tag traffic-guard-ecr-repo:latest 469694857107.dkr.ecr.eu-central-1.amazonaws.com/traffic-guard-ecr-repo:latest && ^
docker push 469694857107.dkr.ecr.eu-central-1.amazonaws.com/traffic-guard-ecr-repo:latest && ^
cd scripts
