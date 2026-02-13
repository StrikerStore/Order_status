# Use official Java image
FROM eclipse-temurin:17-jdk

WORKDIR /app

# Copy entire project
COPY . .

# Build the project
RUN ./mvnw clean package -DskipTests

# Run the jar
ENTRYPOINT ["java", "-jar", "target/Order_status-0.0.1-SNAPSHOT.jar"]

