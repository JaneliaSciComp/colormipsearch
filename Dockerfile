FROM azul/zulu-openjdk:24.0.1-jdk AS builder
ARG GIT_BRANCH=main
ARG GIT_COMMIT=a2701b58

RUN apt-get update -y \
 && apt-get install -y ntp \
 && apt-get install -y git curl

RUN curl -sSL https://bit.ly/install-xq | bash

WORKDIR /neuron-search-tools
RUN git clone --branch ${GIT_BRANCH} --depth 2 https://github.com/JaneliaSciComp/colormipsearch.git . \
 && git reset --hard ${GIT_COMMIT}

RUN ./mvnw package -DskipTests \
 && xq -x '/project/artifactId' pom.xml > target-name \
 && xq -x '/project/version' pom.xml > target-version \
 && mv target/$(cat target-name)-$(cat target-version)-jar-with-dependencies.jar target/colormipsearch-jar-with-dependencies.jar \
 && echo "$(cat target-name)-$(cat target-version)" > .commit \
 && echo ${GIT_COMMIT} >> .git_commit

FROM azul/zulu-openjdk:24.0.1

WORKDIR /app
COPY --from=builder /neuron-search-tools/.commit ./.commit
COPY LICENSE /app/LICENSE
COPY --from=builder /neuron-search-tools/target/colormipsearch-jar-with-dependencies.jar ./colormipsearch-jar-with-dependencies.jar
