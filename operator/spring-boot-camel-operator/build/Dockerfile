FROM quay.io/operator-framework/ansible-operator:latest

USER root
RUN yum -y install git && yum clean all && rm -rf /var/cache/yum

RUN ansible-galaxy install git+https://github.com/eformat/spring-boot-camel-role.git

RUN echo $'--- \n\
- version: v1alpha1\n\
  group: spring-boot-camel.eformat.me\n\
  kind: SpringBootCamel\n\
  role: /opt/ansible/roles/spring-boot-camel-role' > ${HOME}/watches.yaml
