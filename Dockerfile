FROM maven:3.9-eclipse-temurin-17
WORKDIR /app
ENV TZ=Asia/Shanghai
COPY target/ticket-assistant-0.0.1-SNAPSHOT.jar /app/ticket-assistant.jar
EXPOSE 19999
ENTRYPOINT ["java", "-jar", "/app/ticket-assistant.jar"]
