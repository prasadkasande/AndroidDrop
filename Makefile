JAR := backend/target/androiddrop-server-0.0.1-SNAPSHOT.jar

# Keeps the server small: files stream to disk, so a 64 MB heap is plenty even for multi-GB transfers. Without these
# Java reserves up to a quarter of the Mac's memory. Measured: ~190 MB → ~90–150 MB resident.
JAVA_OPTS := -Xmx64m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=24m \
	-XX:MaxMetaspaceSize=96m -XX:+UseCompactObjectHeaders

.PHONY: build run dev-server dev-web test clean

## Build the React UI into the Spring Boot jar. "clean" drops the previous build's UI files, which would otherwise
## pile up inside the jar (every build has new file names).
build:
	cd frontend && npm install && npm run build
	cd backend && ./mvnw -q -DskipTests clean package

## Build, then start the server (http://localhost:8080).
run: build
	java $(JAVA_OPTS) -jar $(JAR)

## Backend only, with the last UI build (use alongside dev-web).
dev-server:
	cd backend && ./mvnw spring-boot:run

## Vite dev server with hot reload on http://localhost:5173, proxying /api to :8080.
dev-web:
	cd frontend && npm run dev

test:
	cd backend && ./mvnw test
	cd frontend && npm run typecheck

clean:
	rm -rf backend/target backend/src/main/resources/static frontend/dist
