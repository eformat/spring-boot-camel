#!groovy
/*
  Intended to run from a separate project where you have deployed Jenkins.
  To allow the jenkins service account to create projects:

  oc adm policy add-cluster-role-to-user self-provisioner system:serviceaccount:$(oc project -q):jenkins
  oc adm policy add-cluster-role-to-user view system:serviceaccount:$(oc project -q):jenkins
 */
pipeline {
    environment {
        GIT_SSL_NO_VERIFY = 'true'
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
        string(name: 'DEV_REPLICA_COUNT', defaultValue: '1', description: "Number of development pods we desire")
        string(name: 'DEV_TAG', defaultValue: 'latest', description: "Development tag")
        string(name: 'TEST_REPLICA_COUNT', defaultValue: '1', description: "Number of test pods we desire")
        string(name: 'TEST_TAG', defaultValue: 'test', description: "Test tag")
        string(name: 'PROJECT_PER_DEV_BUILD', defaultValue: 'false', description: "Create A Project Per Dev Build (true || false)")
        string(name: 'PROJECT_PER_TEST_BUILD', defaultValue: 'false', description: "Create A Project Per Test Build (true || false)")
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
                        env.GIT_BRANCH = "${env.BRANCH_NAME}".toLowerCase()
                        echo "env.GIT_BRANCH is: ${env.GIT_BRANCH}"
                    }
                    // project per build
                    if ("${params.PROJECT_PER_DEV_BUILD}"=='true') {
                        env.DEV_PROJECT = "${params.APP_NAME}-dev-${env.GIT_BRANCH}-${env.BUILD_NUMBER}"
                    } else {
                        env.DEV_PROJECT = "${params.APP_NAME}-dev"
                    }
                    // project per test
                    if ("${params.PROJECT_PER_TEST_BUILD}"=='true') {
                        env.TEST_PROJECT = "${params.APP_NAME}-test-${env.GIT_BRANCH}-${env.BUILD_NUMBER}"
                    } else {
                        env.TEST_PROJECT = "${params.APP_NAME}-test"
                    }
                }
            }
        }

        stage('create dev project') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withProject() {
                            return !openshift.selector("project", "${env.DEV_PROJECT}").exists();
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject() {
                                openshift.newProject("${env.DEV_PROJECT}")
                                sh "oc policy add-role-to-user view --serviceaccount=default -n ${env.DEV_PROJECT}"
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
                            openshift.withProject("${env.DEV_PROJECT}") {
                                checkout([$class           : 'GitSCM',
                                          branches         : [[name: "*/${env.BRANCH_NAME}"]],
                                          userRemoteConfigs: [[url: "${params.GIT_URL}"]],
                                          refspec          : '+refs/pull/*:refs/remotes/origin/pr/*'
                                ]);
                                // maven cache configuration (change mirror host)
                                sh "sed -i \"s|<!-- ### configured mirrors ### -->|<mirror><id>mirror.default</id><url>${params.MAVEN_MIRROR}</url><mirrorOf>external:*</mirrorOf></mirror>|\" /home/jenkins/.m2/settings.xml"
                                def commit_id = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                                echo "${commit_id}"
                                if (openshift.selector("dc", "${params.APP_NAME}").exists()) {
                                    sh "oc set triggers dc/${params.APP_NAME} --manual -n ${env.DEV_PROJECT}"
                                }
                                sh "mvn clean fabric8:deploy -Dfabric8.namespace=${env.DEV_PROJECT}"
                                if (fileExists("configuration/${params.APP_NAME}-dev/application.yml")) {
                                    sh "oc create configmap ${params.APP_NAME} -n ${env.DEV_PROJECT} --from-file=configuration/${params.APP_NAME}-dev/application.yml --dry-run -o yaml | oc apply --force -n ${env.DEV_PROJECT} -f-"
                                }
                                // TODO: push to nexus
                                def pom = readMavenPom file: "pom.xml"
                                appVersion = pom.version
                                artifactId = pom.artifactId
                                groupId = pom.groupId.replace(".", "/")
                                packaging = pom.packaging
                                NEXUS_ARTIFACT_PATH = "${groupId}/${artifactId}/${appVersion}/${artifactId}-${appVersion}.${packaging}"
                                // watch deployment
                                openshift.selector("dc", "${params.APP_NAME}").rollout().status("-w")
                                sh "oc set triggers dc/${params.APP_NAME} --auto -n ${env.DEV_PROJECT}"
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
                            openshift.withProject("${env.DEV_PROJECT}") {
                                openshift.selector("dc", "${params.APP_NAME}").scale("--replicas=${params.DEV_REPLICA_COUNT}")
                                openshift.selector("dc", "${params.APP_NAME}").related('pods').untilEach("${params.DEV_REPLICA_COUNT}".toInteger()) {
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
                        openshift.withProject("${env.DEV_PROJECT}") {
                            return !openshift.selector("route", "${params.APP_NAME}").exists();
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${env.DEV_PROJECT}") {
                                openshift.selector("svc", "${params.APP_NAME}").expose()
                            }
                        }
                    }
                }
            }
        }

/*        stage('test deployment') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    input 'Do you approve deployment to Test environment ?'
                }
            }
        }*/

        stage('create test project') {
            when {
                expression {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject() {
                                return !openshift.selector("project", "${env.TEST_PROJECT}").exists();
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
                                openshift.newProject("${env.TEST_PROJECT}")
                                sh "oc policy add-role-to-user view --serviceaccount=default -n ${env.TEST_PROJECT}"
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
                            openshift.withProject("${env.DEV_PROJECT}") {
                                def testImage = "docker-registry.default.svc.local:5000" + '\\/' + "${env.TEST_PROJECT}" + '\\/' + "${params.APP_NAME}:${params.TEST_TAG}"
                                def patch1 = $/oc export dc,svc,secret -n "${env.DEV_PROJECT}" -l project="${params.APP_NAME}" --as-template="${params.APP_NAME}"-template | oc process -f- | sed -e $'s/\"image\":.*/\"image\": \"${testImage}\",/' -e $'s/\"namespace\":.*/\"namespace\": \"${env.TEST_PROJECT}\"/' | sed -e $'s/\"name\": \"${params.APP_NAME}:${params.DEV_TAG}\",/\"name\": \"${params.APP_NAME}:${params.TEST_TAG}\",/' | oc apply --force -n "${env.TEST_PROJECT}" -f- /$
                                sh patch1
                                if (fileExists("configuration/${params.APP_NAME}-test/application.yml")) {
                                    sh "oc create configmap ${params.APP_NAME} -n ${env.TEST_PROJECT} --from-file=configuration/${params.APP_NAME}-test/application.yml --dry-run -o yaml | oc apply --force -n ${env.TEST_PROJECT} -f-"
                                }
                                openshift.tag("${env.DEV_PROJECT}/${params.APP_NAME}:${params.DEV_TAG}", "${env.TEST_PROJECT}/${params.APP_NAME}:${params.TEST_TAG}")
                            }
                            openshift.withProject("${env.TEST_PROJECT}") {
                                openshift.selector("dc", "${params.APP_NAME}").rollout().status("-w")
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
                            openshift.withProject("${env.TEST_PROJECT}") {
                                openshift.selector("dc", "${params.APP_NAME}").scale("--replicas=${params.TEST_REPLICA_COUNT}")
                                openshift.selector("dc", "${params.APP_NAME}").related('pods').untilEach("${params.TEST_REPLICA_COUNT}".toInteger()) {
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
                        openshift.withProject("${env.TEST_PROJECT}") {
                            return !openshift.selector("route", "${params.APP_NAME}").exists();
                        }
                    }
                }
            }
            steps {
                script {
                    openshift.withCluster() {
                        openshift.withCredentials() {
                            openshift.withProject("${env.TEST_PROJECT}") {
                                openshift.selector("svc", "${params.APP_NAME}").expose()
                            }
                        }
                    }
                }
            }
        }
    }
}
