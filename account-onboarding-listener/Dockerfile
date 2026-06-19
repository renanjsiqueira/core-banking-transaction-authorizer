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
COPY account-onboarding-listener/src account-onboarding-listener/src

RUN mvn -q -pl account-onboarding-listener -am -DskipTests package

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/account-onboarding-listener/target/account-onboarding-listener.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
