FROM openjdk:15.0.1-oracle
LABEL maintainer="Denis Talochkin"
ADD target/traffic-guard-stream-consumer-0.0.1-SNAPSHOT.jar traffic-guard-stream-consumer.jar
ENTRYPOINT ["java", "--enable-preview", "-Dspring.profiles.active=deploy_to_ecs", "-jar", "traffic-guard-stream-consumer.jar"]