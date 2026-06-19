# --- Build stage: build this module and its reactor dependency (core-banking-commons-domain) ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy all module POMs so the Maven reactor can resolve the aggregator model.
COPY pom.xml .
COPY core-banking-commons-domain/pom.xml core-banking-commons-domain/pom.xml
COPY core-banking-transaction-authorizer-api/pom.xml core-banking-transaction-authorizer-api/pom.xml
COPY core-banking-account-created-listener/pom.xml core-banking-account-created-listener/pom.xml

# Copy sources needed to build this service (+ core-banking-commons-domain via -am).
COPY core-banking-commons-domain/src core-banking-commons-domain/src
COPY core-banking-transaction-authorizer-api/src core-banking-transaction-authorizer-api/src

RUN mvn -q -pl core-banking-transaction-authorizer-api -am -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/core-banking-transaction-authorizer-api/target/core-banking-transaction-authorizer-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
