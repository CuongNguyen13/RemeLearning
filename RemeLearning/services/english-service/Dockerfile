# Build stage: compiles the Maven reactor (common + english-service) and packages the jar
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY services/bff-service/pom.xml services/bff-service/pom.xml
COPY services/user-service/pom.xml services/user-service/pom.xml
COPY services/recording-service/pom.xml services/recording-service/pom.xml
COPY services/english-service/pom.xml services/english-service/pom.xml
COPY services/recommendation-service/pom.xml services/recommendation-service/pom.xml
COPY services/dashboard-service/pom.xml services/dashboard-service/pom.xml

COPY common/src common/src
COPY services/english-service/src services/english-service/src

RUN chmod +x mvnw && ./mvnw -pl services/english-service -am clean package -DskipTests

# Runtime stage: minimal JRE image running the packaged jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/services/english-service/target/english-service-*.jar app.jar
COPY services/english-service/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
EXPOSE 8085
ENTRYPOINT ["./entrypoint.sh"]
