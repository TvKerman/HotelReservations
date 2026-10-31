FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/pom.xml common/
COPY discovery-service/pom.xml discovery-service/
COPY gateway-service/pom.xml gateway-service/
COPY auth-service/pom.xml auth-service/
COPY hotel-service/pom.xml hotel-service/
COPY booking-service/pom.xml booking-service/

RUN mvn -B -pl auth-service -am dependency:go-offline

COPY common/src common/src
COPY auth-service/src auth-service/src
RUN mvn -B -pl auth-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/auth-service/target/*-SNAPSHOT.jar app.jar

RUN addgroup -S app && adduser -S app -G app && chown app:app /app
USER app

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]