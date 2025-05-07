FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY src/Server.java .
RUN javac Server.java
EXPOSE 8888
CMD ["java", "Server"]