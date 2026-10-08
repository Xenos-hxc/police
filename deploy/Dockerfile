FROM eclipse-temurin:21-jre-jammy

RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system railway && useradd --system --gid railway railway \
    && mkdir -p /app/data/uploads/.multipart /app/data/logs \
    && chown -R railway:railway /app
WORKDIR /app
COPY --chown=railway:railway backend/target/railway-security-check.jar /app/app.jar
USER railway
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_PATH=/app/data/uploads \
    UPLOAD_TEMP_PATH=/app/data/uploads/.multipart \
    LOG_PATH=/app/data/logs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
