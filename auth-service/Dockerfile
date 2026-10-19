FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY discovery-service/pom.xml discovery-service/
COPY gateway-service/pom.xml gateway-service/
COPY hotel-service/pom.xml hotel-service/
COPY booking-service/pom.xml booking-service/
COPY auth-service/pom.xml auth-service/
COPY common/pom.xml common/
RUN mvn -B -pl auth-service -am dependency:go-offline
COPY common common
COPY auth-service auth-service
RUN mvn -B -pl auth-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/auth-service/target/*.jar app.jar
RUN addgroup -S app && adduser -S app -G app && chown app:app /app
USER app
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]