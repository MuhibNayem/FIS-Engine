FROM eclipse-temurin:25-jre

RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /home/app app

WORKDIR /app
COPY --chown=app:app build/libs/*.jar /app/app.jar
EXPOSE 8080
USER app:app
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
