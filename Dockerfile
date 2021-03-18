FROM openjdk:15.0.1-oracle
LABEL maintainer="Denis Talochkin"
ADD target/traffic-guard-stream-consumer-0.0.1-SNAPSHOT.jar traffic-guard-stream-consumer.jar
EXPOSE 8080/tcp
ENTRYPOINT ["java", "--enable-preview", "-jar", "traffic-guard-stream-consumer.jar"]