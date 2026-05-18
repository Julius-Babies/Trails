FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY web_build /app/webstatic
COPY server-all.jar /app/server.jar

ENTRYPOINT ["java", "-jar", "server.jar", "--static-web-path=/app/webstatic"]