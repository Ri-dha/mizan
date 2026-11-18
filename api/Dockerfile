FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /build/target/mizan-api-*.jar app.jar
EXPOSE 8080
# Sized for one small instance (measured about 480 MB in use instead of 620 MB with the JVM's
# defaults): the serial collector suits a small heap on one or two CPUs, and capped heap,
# metaspace and code cache stop the JVM sizing itself to the host. Override with JAVA_OPTS.
ENV JAVA_OPTS="-XX:+UseSerialGC -Xmx192m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
