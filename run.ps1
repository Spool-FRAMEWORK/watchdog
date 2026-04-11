mvn clean package
docker stop watchdog
docker rm watchdog
docker build -t watchdog .
docker run -d --name watchdog -p 8090:8090 watchdog