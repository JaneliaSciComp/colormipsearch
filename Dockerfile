FROM azul/zulu-openjdk:23.0.2-jdk AS builder
ARG GIT_BRANCH=main
ARG COMMIT_HASH=33305292

RUN apt update -y; \
    apt install -y git curl; \
    curl -sSL https://bit.ly/install-xq | bash;

WORKDIR /neuron-search-tools
RUN git clone --branch ${GIT_BRANCH} --depth 2 https://github.com/JaneliaSciComp/colormipsearch.git .; \
    git reset --hard ${COMMIT_HASH}

RUN ./mvnw package -DskipTests; \
    xq -x '/project/artifactId' pom.xml > target-name; \
    xq -x '/project/version' pom.xml > target-version; \
    mv target/$(cat target-name)-$(cat target-version)-jar-with-dependencies.jar target/colormipsearch-jar-with-dependencies.jar; \
    echo "$(cat target-name)-$(cat target-version)" > .commit; \
    echo ${COMMIT_HASH} >> .commit

FROM azul/zulu-openjdk:23.0.2

WORKDIR /app
COPY --from=builder /neuron-search-tools/.commit ./.commit
COPY LICENSE /app/LICENSE
COPY --from=builder /neuron-search-tools/target/colormipsearch-jar-with-dependencies.jar ./colormipsearch-jar-with-dependencies.jar
