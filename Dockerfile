FROM openjdk:latest

RUN mkdir /jaeger
WORKDIR /jaeger

COPY lib /jaeger/
COPY target/jaeger-performance-jar-with-dependencies.jar /jaeger/jaeger-performance.jar

CMD ["java","-jar","jaeger-performance.jar"]
