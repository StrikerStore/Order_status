# Use OpenJDK 17
FROM eclipse-temurin:17-jre

# Set working directory
WORKDIR /app

# Copy the JAR file (built by Railway or locally)
COPY target/order-tracking-service-*.jar app.jar

# Expose port (Railway will set PORT env var)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
