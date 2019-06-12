FROM quay.io/operator-framework/ansible-operator:v0.8.0

USER root
RUN yum -y install git

RUN ansible-galaxy install git+https://github.com/eformat/spring-boot-camel-role.git

RUN echo $'--- \n\
- version: v1alpha1\n\
  group: spring-boot-camel.eformat.me\n\
  kind: SpringBootCamel\n\
  role: /opt/ansible/roles/spring-boot-camel-role' > ${HOME}/watches.yaml
