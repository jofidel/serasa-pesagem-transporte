FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /work
COPY --from=build /build/target/quarkus-app/lib/ ./lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ ./app/
COPY --from=build /build/target/quarkus-app/quarkus/ ./quarkus/
EXPOSE 8080
ENTRYPOINT ["java","-jar","quarkus-run.jar"]
