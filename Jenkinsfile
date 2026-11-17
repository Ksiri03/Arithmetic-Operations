pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '5'))
    }

    environment {
        APP_NAME      = 'arithmetic-ops'
        IMAGE_TAG     = "build-${env.BUILD_NUMBER}"
        K8S_NAMESPACE = 'arithmetic'
        KIND_CLUSTER  = 'arithmetic-lab'
        APP_URL       = 'http://localhost:30081'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                bat 'git rev-parse --short HEAD && git log -1 --pretty=format:"%%h %%s"'
            }
        }

        stage('Compile and test') {
            steps {
                bat 'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\test.ps1'
            }
        }

        stage('Build Docker image') {
            steps {
                bat 'docker build -t %APP_NAME%:%IMAGE_TAG% -t %APP_NAME%:latest .'
                bat 'docker image inspect %APP_NAME%:%IMAGE_TAG% --format "image={{.Id}} size={{.Size}}"'
            }
        }

        stage('Load image into kind') {
            steps {
                bat '''
                    kind get clusters | findstr /C:"%KIND_CLUSTER%" >nul || kind create cluster --name %KIND_CLUSTER% --config k8s\\kind-config.yaml
                    kind load docker-image %APP_NAME%:%IMAGE_TAG% --name %KIND_CLUSTER%
                '''
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                bat '''
                    kubectl apply -k k8s
                    kubectl -n %K8S_NAMESPACE% set image deployment/arithmetic-app app=%APP_NAME%:%IMAGE_TAG%
                    kubectl -n %K8S_NAMESPACE% rollout status deployment/arithmetic-app --timeout=180s
                    kubectl -n %K8S_NAMESPACE% get pods -o wide
                    kubectl -n %K8S_NAMESPACE% get service arithmetic-service
                '''
            }
        }

        stage('Smoke test') {
            steps {
                bat 'powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\smoke-test.ps1 -BaseUrl %APP_URL%'
            }
        }
    }

    post {
        success {
            echo "Deployed ${env.APP_NAME}:${env.IMAGE_TAG} to kind cluster ${env.KIND_CLUSTER}. Open ${env.APP_URL}"
        }
        failure {
            bat '''
                kubectl -n arithmetic get pods -o wide || exit 0
                kubectl -n arithmetic describe deployment/arithmetic-app || exit 0
                docker images | findstr arithmetic-ops || exit 0
            '''
        }
        always {
            archiveArtifacts artifacts: 'build/arithmetic-ops.jar', allowEmptyArchive: true
            deleteDir()
        }
    }
}
