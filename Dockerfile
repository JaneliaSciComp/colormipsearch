FROM azul/zulu-openjdk:21.0.2-jdk AS builder
ARG TARGETPLATFORM
ARG GIT_BRANCH=imglib2
ARG COMMIT_HASH=39ef0bc5

RUN apt update && \
    apt install -y git

WORKDIR /neuron-search-tools
RUN git clone --branch ${GIT_BRANCH} --depth 2 https://github.com/JaneliaSciComp/colormipsearch.git . && \
    git reset --hard ${COMMIT_HASH}

RUN ./mvnw package -DskipTests && \
    echo ${COMMIT_HASH} > .commit

FROM azul/zulu-openjdk:21.0.2-jdk
ARG TARGETPLATFORM

WORKDIR /app
COPY --from=builder /neuron-search-tools/.commit ./.commit
COPY --from=builder /neuron-search-tools/target/colormipsearch-3.1.0-jar-with-dependencies.jar ./colormipsearch-3.1.0-jar-with-dependencies.jar
