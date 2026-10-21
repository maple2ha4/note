FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml ./
COPY backend/src ./src
RUN mvn -B -ntp verify

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --gid 10001 notes && useradd --uid 10001 --gid notes --no-create-home notes && mkdir -p /data /app/backend /app/frontend && chown 10001:10001 /data
WORKDIR /app/backend
COPY --from=backend /build/target/notes-1.0.0.jar ./notes.jar
COPY --from=frontend /build/dist /app/frontend/dist
ENV NOTES_DATA_DIR=/data BIND_ADDRESS=0.0.0.0 PUBLIC_ACCESS_ENABLED=false
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","notes.jar"]
