ARG NEXUS_VERSION=3.15.2

FROM maven:3-jdk-8-alpine AS build
ARG NEXUS_VERSION=3.15.2
ARG NEXUS_BUILD=01

#----build in container
#COPY . /nexus-repository-helm/
#RUN cd /nexus-repository-helm/; sed -i "s/3.15.2-01/${NEXUS_VERSION}-${NEXUS_BUILD}/g" pom.xml; \
#    mvn clean package --settings settings.xml;

#----use already built artifact
RUN mkdir -p /nexus-repository-helm/target
COPY ./target/*.jar /nexus-repository-helm/target/

FROM sonatype/nexus3:$NEXUS_VERSION
ARG NEXUS_VERSION=3.15.2
ARG NEXUS_BUILD=01
ARG HELM_VERSION=0.0.8
ARG COMP_VERSION=1.18
ARG TARGET_DIR=/opt/sonatype/nexus/system/org/sonatype/nexus/plugins/nexus-repository-helm/${HELM_VERSION}/
USER root
RUN mkdir -p ${TARGET_DIR}; \
    sed -i 's@nexus-repository-maven</feature>@nexus-repository-maven</feature>\n        <feature prerequisite="false" dependency="false">nexus-repository-helm</feature>@g' /opt/sonatype/nexus/system/org/sonatype/nexus/assemblies/nexus-core-feature/${NEXUS_VERSION}-${NEXUS_BUILD}/nexus-core-feature-${NEXUS_VERSION}-${NEXUS_BUILD}-features.xml; \
    sed -i 's@<feature name="nexus-repository-maven"@<feature name="nexus-repository-helm" description="org.sonatype.nexus.plugins:nexus-repository-helm" version="'"${HELM_VERSION}"'">\n        <details>org.sonatype.nexus.plugins:nexus-repository-helm</details>\n        <bundle>mvn:org.sonatype.nexus.plugins/nexus-repository-helm/'"${HELM_VERSION}"'</bundle>\n        <bundle>mvn:org.apache.commons/commons-compress/'"${COMP_VERSION}"'</bundle>\n   </feature>\n    <feature name="nexus-repository-maven"@g' /opt/sonatype/nexus/system/org/sonatype/nexus/assemblies/nexus-core-feature/${NEXUS_VERSION}-${NEXUS_BUILD}/nexus-core-feature-${NEXUS_VERSION}-${NEXUS_BUILD}-features.xml;
COPY --from=build /nexus-repository-helm/target/nexus-repository-helm-${HELM_VERSION}.jar ${TARGET_DIR}
USER nexus
