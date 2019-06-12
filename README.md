# Spring-Boot Camel QuickStart

This example demonstrates how you can use Apache Camel with Spring Boot based on a [fabric8 Java base image](https://github.com/fabric8io/base-images#java-base-images).

The quickstart uses Spring Boot to configure a little application that includes a Camel
route that triggers a message every 5th second, and routes the message to a log.


### Building

The example can be built with

    mvn clean install


### Running the example locally

The example can be run locally using the following Maven goal:

    mvn spring-boot:run


### Running the example in Kubernetes

It is assumed a running Kubernetes platform is already running. If not you can find details how to [get started](http://fabric8.io/guide/getStarted/index.html).

Assuming your current shell is connected to Kubernetes or OpenShift so that you can type a command like

```
kubectl get pods
```

or for OpenShift

```
oc get pods
```

Then the following command will package your app and run it on Kubernetes:

```
mvn fabric8:run
```

To list all the running pods:

    oc get pods

Then find the name of the pod that runs this quickstart, and output the logs from the running pods with:

    oc logs <name of pod>

You can also use the [fabric8 developer console](http://fabric8.io/guide/console.html) to manage the running pods, and view logs and much more.


### More details

You can find more details about running this [quickstart](http://fabric8.io/guide/quickstarts/running.html) on the website. This also includes instructions how to change the Docker image user and registry.

#### CI/CD Demo

```
oc new-project ci-cd --description "CI/CD" --display-name="CI/CD"
oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default
oc adm policy add-cluster-role-to-user self-provisioner system:serviceaccount:$(oc project -q):jenkins
oc adm policy add-cluster-role-to-user view system:serviceaccount:$(oc project -q):jenkins
oc -n openshift process jenkins-persistent | oc create -f-
oc process -f helloservice-pipeline-bc.yaml | oc apply -f-
oc start-build helloservice
```

#### Multi-branch pipeline

Use the Jenkinsfile in base directory as part of multibranch pipeline build for Pull Requests.

#### Operators

Ansible Operator based deployment

Build application image:

```
-- build image in openshift namespace for now
mvn package
oc -n openshift new-build --binary --name=spring-boot-camel -l app=spring-boot-camel -i fuse7-java-openshift:1.2
oc -n openshift start-build spring-boot-camel --from-file=./target/helloservice-1.0-SNAPSHOT.jar --follow

```

The application ansible role is here:

```
https://github.com/eformat/spring-boot-camel-role.git
```

Build the operator image

```
cd spring-boot-camel/operator/spring-boot-camel-operator

podman build --no-cache -t spring-boot-camel-operator:v0.0.1 -f build/Dockerfile .
```

Setup podman locally

```
HOST=$(oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}')
sudo mkdir -p /etc/containers/certs.d/$HOST
echo $(oc get secrets router-ca --template='{{index .data "tls.crt"}}' -n openshift-ingress-operator) | base64 -d | sudo tee /etc/containers/certs.d/$HOST/ca.crt
podman login -u $(oc whoami) -p $(oc whoami -t) $HOST
```

Deploy operator image

```
oc new-project spring-boot-camel
oc policy add-role-to-user view system:serviceaccount:$(oc project -q):default
--
podman tag localhost/spring-boot-camel-operator:v0.0.1 $HOST/spring-boot-camel/spring-boot-camel-operator:v0.0.1
podman login -u $(oc whoami) -p $(oc whoami -t) $HOST
podman push $HOST/spring-boot-camel/spring-boot-camel-operator:v0.0.1
```

Create CR, CRD, Kube objects (admin privilege)

```
oc apply -f deploy/crds/springbootcamel_v1alpha1_springbootcamel_crd.yaml
oc apply -f deploy/service_account.yaml
oc apply -f deploy/role.yaml
oc apply -f deploy/role_binding.yaml
oc apply -f deploy/operator.yaml
```

Create CR - Application (adjust values to suit)

```
oc apply -f deploy/crds/springbootcamel_v1alpha1_springbootcamel_cr.yaml
```

Delete Application

```
oc delete -f deploy/crds/springbootcamel_v1alpha1_springbootcamel_cr.yaml
-- OR
oc delete SpringBootCamel
```

Delete operator and configs

```
oc delete -f deploy/operator.yaml
oc delete -f deploy/service_account.yaml
oc delete -f deploy/role.yaml
oc delete -f deploy/role_binding.yaml
```
