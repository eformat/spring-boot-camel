#!groovy
/*
  Intended to run from a separate project where you have deployed Jenkins.
  To allow the jenkins service account to create projects:

  oc adm policy add-cluster-role-to-user self-provisioner system:serviceaccount:$(oc project -q):jenkins
  oc adm policy add-cluster-role-to-user view system:serviceaccount:$(oc project -q):jenkins
 */
pipeline {
    environment {
        GIT_SSL_NO_VERIFY = true
    }
    options {
        // set a timeout of 20 minutes for this pipeline
        timeout(time: 20, unit: 'MINUTES')
        // when running Jenkinsfile from SCM using jenkinsfilepath the node implicitly does a checkout
        skipDefaultCheckout()
    }
    agent {
        label 'maven'
    }
    parameters {
        string(name: 'APP_NAME', defaultValue: 'helloservice', description: "Application Name - all resources use this name as a label")
        string(name: 'GIT_URL', defaultValue: 'https://github.com/eformat/spring-boot-camel.git', description: "Project Git URL)")
        string(name: 'GIT_BRANCH', defaultValue: 'master', description: "Git Branch (from Multibranch plugin if being used)")        
        string(name: 'DEV_PROJECT', defaultValue: 'spring-boot-camel-dev', description: "Name of the Development namespace")
        string(name: 'DEV_REPLICA_COUNT', defaultValue: '1', description: "Number of development pods we desire")
        string(name: 'DEV_TAG', defaultValue: 'latest', description: "Development tag")
        string(name: 'TEST_PROJECT', defaultValue: 'spring-boot-camel-test', description: "Name of the Test namespace")
        string(name: 'TEST_REPLICA_COUNT', defaultValue: '1', description: "Number of test pods we desire")
        string(name: 'TEST_TAG', defaultValue: 'test', description: "Test tag")
        string(name: 'PROJECT_PER_DEV_BUILD', defaultValue: 'true', description: "Create A Project Per Dev Build (true || false)")
        string(name: 'PROJECT_PER_TEST_BUILD', defaultValue: 'true', description: "Create A Project Per Test Build (true || false)")
        string(name: 'MAVEN_MIRROR', defaultValue: 'http://nexus.nexus.svc.cluster.local:8081/repository/maven-public/', description: "Maven Mirror")
    }
    stages {
        stage('initialise') {
            steps {
                script {
                    echo "Build Number is: ${env.BUILD_NUMBER}"
                    echo "Job Name is: ${env.JOB_NAME}"
                    echo "Branch name is: ${env.BRANCH_NAME}"
                    sh "oc version"
                    sh 'printenv'
                    if ("${env.BRANCH_NAME}".length()>0) {
                        GIT_BRANCH = "${env.BRANCH_NAME}".toLowerCase()
                    }
                    // project per build
                    if ("${PROJECT_PER_DEV_BUILD}"=='true') {
                        DEV_PROJECT = "${APP_NAME}-dev-${GIT_BRANCH}-${env.BUILD_NUMBER}"
                    } else {
                        DEV_PROJECT = "${APP_NAME}-dev"
                    }
                    // project per test
                    if ("${PROJECT_PER_TEST_BUILD}"=='true') {
                        TEST_PROJECT = "${APP_NAME}-test-${GIT_BRANCH}-${env.BUILD_NUMBER}"
                    } else {
                        TEST_PROJECT = "${APP_NAME}-test"
                    }
                }
            }
        }

        stage('create dev project') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject() {
                            return !openshift.selector("project", "${DEV_PROJECT}").exists();
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject() {
                                openshift.newProject("${DEV_PROJECT}")
                                sh "oc policy add-role-to-user view --serviceaccount=default -n ${DEV_PROJECT}"
                            }
                        }
                    }
                }
            }
        }

        stage('build and deploy dev') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${DEV_PROJECT}") {
                                checkout([$class           : 'GitSCM',
                                          branches         : [[name: "*/${GIT_BRANCH}"]],
                                          userRemoteConfigs: [[url: "${GIT_URL}"]]
                                ]);
                                // maven cache configuration (change mirror host)
                                sh "sed -i \"s|<!-- ### configured mirrors ### -->|<mirror><id>mirror.default</id><url>${MAVEN_MIRROR}</url><mirrorOf>external:*</mirrorOf></mirror>|\" /home/jenkins/.m2/settings.xml"
                                def commit_id = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                                echo "${commit_id}"
                                if (openshift.selector("dc", "${APP_NAME}").exists()) {
                                    sh "oc set triggers dc/${APP_NAME} --manual -n ${DEV_PROJECT}"
                                }
                                sh "mvn clean fabric8:deploy -Dfabric8.namespace=${DEV_PROJECT}"
                                if (fileExists("configuration/${APP_NAME}-dev/application.yml")) {
                                    sh "oc create configmap ${APP_NAME} -n ${DEV_PROJECT} --from-file=configuration/${APP_NAME}-dev/application.yml --dry-run -o yaml | oc apply --force -n ${DEV_PROJECT} -f-"
                                }
                                // TODO: push to nexus
                                def pom = readMavenPom file: "pom.xml"
                                appVersion = pom.version
                                artifactId = pom.artifactId
                                groupId = pom.groupId.replace(".", "/")
                                packaging = pom.packaging
                                NEXUS_ARTIFACT_PATH = "${groupId}/${artifactId}/${appVersion}/${artifactId}-${appVersion}.${packaging}"
                                // watch deployment
                                openshift.selector("dc", "${APP_NAME}").rollout().status("-w")
                                sh "oc set triggers dc/${APP_NAME} --auto -n ${DEV_PROJECT}"
                            }
                        }
                    }
                }
            }
        }

        stage('scale replicas dev') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${DEV_PROJECT}") {
                                openshift.selector("dc", "${APP_NAME}").scale("--replicas=${DEV_REPLICA_COUNT}")
                                openshift.selector("dc", "${APP_NAME}").related('pods').untilEach("${DEV_REPLICA_COUNT}".toInteger()) {
                                    return (it.object().status.phase == "Running")
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('create dev route') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject("${DEV_PROJECT}") {
                            return !openshift.selector("route", "${APP_NAME}").exists();
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${DEV_PROJECT}") {
                                openshift.selector("svc", "${APP_NAME}").expose()
                            }
                        }
                    }
                }
            }
        }

        stage('test deployment') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    input 'Do you approve deployment to Test environment ?'
                }
            }
        }

        stage('create test project') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject() {
                                return !openshift.selector("project", "${TEST_PROJECT}").exists();
                            }
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject() {
                                openshift.newProject("${TEST_PROJECT}")
                                sh "oc policy add-role-to-user view --serviceaccount=default -n ${TEST_PROJECT}"
                            }
                        }
                    }
                }
            }
        }

        stage('promote to test') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${DEV_PROJECT}") {
                                def testImage = "docker-registry.default.svc.local:5000" + '\\/' + "${TEST_PROJECT}" + '\\/' + "${APP_NAME}:${TEST_TAG}"
                                def patch1 = $/oc export dc,svc,secret -n "${DEV_PROJECT}" -l project="${APP_NAME}" --as-template="${APP_NAME}"-template | oc process -f- | sed -e $'s/\"image\":.*/\"image\": \"${testImage}\",/' -e $'s/\"namespace\":.*/\"namespace\": \"${TEST_PROJECT}\"/' | sed -e $'s/\"name\": \"${APP_NAME}:${DEV_TAG}\",/\"name\": \"${APP_NAME}:${TEST_TAG}\",/' | oc apply --force -n "${TEST_PROJECT}" -f- /$
                                sh patch1
                                if (fileExists("configuration/${APP_NAME}-test/application.yml")) {
                                    sh "oc create configmap ${APP_NAME} -n ${TEST_PROJECT} --from-file=configuration/${APP_NAME}-test/application.yml --dry-run -o yaml | oc apply --force -n ${TEST_PROJECT} -f-"
                                }
                                openshift.tag("${DEV_PROJECT}/${APP_NAME}:${DEV_TAG}", "${TEST_PROJECT}/${APP_NAME}:${TEST_TAG}")
                            }
                            openshift.withProject("${TEST_PROJECT}") {
                                openshift.selector("dc", "${APP_NAME}").rollout().status("-w")
                            }
                        }
                    }
                }
            }
        }

        stage('scale replicas test') {
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${TEST_PROJECT}") {
                                openshift.selector("dc", "${APP_NAME}").scale("--replicas=${TEST_REPLICA_COUNT}")
                                openshift.selector("dc", "${APP_NAME}").related('pods').untilEach("${TEST_REPLICA_COUNT}".toInteger()) {
                                    return (it.object().status.phase == "Running")
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('create test route') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject("${TEST_PROJECT}") {
                            return !openshift.selector("route", "${APP_NAME}").exists();
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${TEST_PROJECT}") {
                                openshift.selector("svc", "${APP_NAME}").expose()
                            }
                        }
                    }
                }
            }
        }
    }
}
