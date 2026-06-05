# ===== Stage 1: build =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copia o wrapper e o pom primeiro para aproveitar cache de dependências
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copia o código e empacota (pulando testes para acelerar o build da imagem)
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ===== Stage 2: runtime =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copia o jar gerado (demo-0.0.1-SNAPSHOT.jar)
COPY --from=build /app/target/*.jar app.jar
# Índice IVF como arquivo standalone, mapeado via mmap em runtime (fora do heap).
# Como app1 e app2 usam a mesma imagem, ambos mapeiam o mesmo arquivo (page cache compartilhado).
COPY --from=build /app/target/classes/static/reference/references.ivf /app/references.ivf

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
