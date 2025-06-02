FROM clojure:tools-deps-bookworm-slim AS builder
WORKDIR /opt
COPY . .
RUN apt-get update
RUN apt-get install -y --no-install-recommends curl bash zip unzip ca-certificates
RUN rm -rf /var/lib/apt/lists/*
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
RUN apt-get install -y nodejs

WORKDIR /opt/core
RUN ./scripts/rsc
RUN clojure -Sdeps '{:mvn/local-repo "./.m2/repository"}' -T:build uber

FROM clojure:tools-deps-bookworm-slim AS runtime
COPY --from=builder /opt /app

RUN apt-get update
RUN apt-get install -y --no-install-recommends curl bash zip unzip ca-certificates
RUN rm -rf /var/lib/apt/lists/*

WORKDIR /app/core
EXPOSE 8080
ENTRYPOINT java -cp target/app.jar uix.rsc_example.server.core
