# Jenkins controller + a docker CLI.
# The stock jenkins/jenkins image cannot talk to the mounted /var/run/docker.sock because it
# ships no docker binary - and every pipeline stage here runs inside a docker agent. This adds
# ONLY the CLI (docker-ce-cli from Docker's apt repo, not the multi-hundred-MB engine): the
# daemon stays the host's, reached through the socket. Group access to the socket comes from
# `group_add` in docker-compose.yml, not from this image.
FROM jenkins/jenkins:lts-jdk21

USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && echo "deb [signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*
USER jenkins
