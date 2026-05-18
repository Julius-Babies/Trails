FROM eclipse-temurin:24-jre-alpine

WORKDIR /app
COPY ./web/build /app/webstatic
COPY ./server/build/libs/server-all.jar /app/server.jar

ENTRYPOINT ["java", "-jar", "server.jar", "--static-web-path /app/webstatic"]