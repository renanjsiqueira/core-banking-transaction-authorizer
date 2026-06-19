# --- Build stage: build this module and its reactor dependency (shared-kernel) ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy all module POMs so the Maven reactor can resolve the aggregator model.
COPY pom.xml .
COPY shared-kernel/pom.xml shared-kernel/pom.xml
COPY transaction-authorization-api/pom.xml transaction-authorization-api/pom.xml
COPY account-onboarding-listener/pom.xml account-onboarding-listener/pom.xml

# Copy sources needed to build this service (+ shared-kernel via -am).
COPY shared-kernel/src shared-kernel/src
COPY transaction-authorization-api/src transaction-authorization-api/src

RUN mvn -q -pl transaction-authorization-api -am -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/transaction-authorization-api/target/transaction-authorization-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
