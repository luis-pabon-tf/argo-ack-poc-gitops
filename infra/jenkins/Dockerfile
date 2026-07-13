FROM jenkins/jenkins:2.568.1-lts-jdk21

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    zip \
    unzip \
    curl \
    python3 \
    python3-pip \
    && pip3 install --no-cache-dir --break-system-packages awscli \
    && rm -rf /var/lib/apt/lists/*

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"
ENV CASC_JENKINS_CONFIG="/usr/share/jenkins/ref/casc.yaml"

COPY casc.yaml /usr/share/jenkins/ref/casc.yaml
COPY seed-job.groovy /usr/share/jenkins/ref/init.groovy.d/seed-job.groovy

USER jenkins
