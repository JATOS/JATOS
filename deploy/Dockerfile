FROM eclipse-temurin:25-jre-jammy

LABEL maintainer="support@jatos.org"

ENV JATOS_HOME=/opt/jatos
ENV JATOS_DATA=/opt/jatos_data

WORKDIR ${JATOS_HOME}

COPY */opt/docker/ ${JATOS_HOME}/
COPY opt/docker/ ${JATOS_HOME}/

RUN groupadd --gid 1000 jatos \
 && useradd --uid 1000 --gid 1000 jatos \
 && mkdir -p ${JATOS_HOME}/logs ${JATOS_DATA} \
 && chown -R jatos:jatos ${JATOS_HOME} ${JATOS_DATA}

USER jatos

EXPOSE 9000

ENV JATOS_DB_URL="jdbc:h2:/opt/jatos_data/database/jatos;MODE=MYSQL;DATABASE_TO_UPPER=FALSE;IGNORECASE=TRUE;DEFAULT_LOCK_TIMEOUT=10000;SELECT_FOR_UPDATE_MVCC=FALSE"
ENV JATOS_STUDY_ASSETS_ROOT_PATH=/opt/jatos_data/study_assets_root
ENV JATOS_RESULT_UPLOADS_PATH=/opt/jatos_data/result_uploads
ENV JATOS_STUDY_LOGS_PATH=/opt/jatos_data/study_logs
ENV JATOS_TMP_PATH=/opt/jatos_data/tmp

ENTRYPOINT ["./loader.sh", "start"]
