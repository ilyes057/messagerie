FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY src/Server.java .
RUN javac Server.java
CMD ["java", "Server"]