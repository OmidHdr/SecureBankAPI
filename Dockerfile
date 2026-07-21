FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup --system spring \
    && adduser --system --ingroup spring spring \
    && mkdir -p /app/logs \
    && chown -R spring:spring /app

WORKDIR /app

COPY --from=build --chown=spring:spring \
    /workspace/target/SecureBankAPI-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
