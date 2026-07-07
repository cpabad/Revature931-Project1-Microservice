# syntax=docker/dockerfile:1
# One parameterized Dockerfile for all three services: docker-compose passes MODULE
# (gateway / auth-service / reimbursement-service) as a build arg, so the build recipe
# lives in exactly one place.

# ---- build stage: full JDK + Maven; never ships ------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG MODULE
# BuildKit cache mount: the local Maven repo survives across builds of all three images,
# so dependencies download once, not three times.
RUN --mount=type=cache,target=/root/.m2 mvn -q -pl ${MODULE} -am package -DskipTests

# ---- runtime stage: JRE only, no compiler, no Maven, no source ----------------------
FROM eclipse-temurin:21-jre
ARG MODULE
# The Boot repackaged jar is the only artifact ending in .jar (the thin original is .jar.original).
COPY --from=build /src/${MODULE}/target/*.jar /app.jar
# Run as a non-root user: a compromised app process shouldn't own the container.
RUN useradd --system --no-create-home ers
USER ers
ENTRYPOINT ["java", "-jar", "/app.jar"]
