# Use a base image with Java 17 installed
FROM openjdk:17-slim
# Set the working directory in the container
WORKDIR /app
# Define the ARG instruction for the jar file name
ARG JAR_FILE=gatling-1.0-SNAPSHOT-all.jar
# Copy the fat jar from your host machine into the container
COPY build/libs/${JAR_FILE} app.jar
# Define the entry point that runs your application
ENTRYPOINT ["java", "-jar", "app.jar"]