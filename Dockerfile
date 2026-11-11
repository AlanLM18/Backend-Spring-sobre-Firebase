# SafeMotion AI · backend Spring Boot sobre Firebase (imagen para Render)
# 1) Compilar con Maven y JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# 2) Imagen final: solo el JRE y el JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 safemotion
COPY --from=build /app/target/safemotion-backend.jar app.jar
USER safemotion
# Render indica el puerto en la variable PORT (application.properties la usa); 8080 en local
ENV PORT=8080
EXPOSE 8080
# Memoria acotada para el plan gratuito de Render (512 MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]
