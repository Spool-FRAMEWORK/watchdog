mvn clean package
docker stop feeder
docker rm feeder
docker build -t feeder .
docker run -d --name feeder -p :8080 feeder