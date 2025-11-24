pipeline {
    agent any

    environment {
        IMAGE = "onedrop-backend:v1"
        CONTAINER = "onedrop-backend"
        NETWORK = "custom_bridge"
        STATIC_IP = "172.25.0.5"
        PORT = "9091"
        ENV_FILE = "/home/envs/onedropbackend.env"
    }

    stages {

        stage('Pull Code') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'main']],
                    userRemoteConfigs: [[
                        url: 'https://github.com/ihazratummar/OneDrop-Backend',
                        credentialsId: 'github-creds'
                    ]]
                ])
            }
        }


        stage('Build Docker Image') {
            steps {
                script {
                    sh """
                        docker build -t ${IMAGE} .
                    """
                    echo "✅ Docker image built"
                }
            }
        }

        stage('Stop & Remove Old Container') {
            steps {
                script {
                    sh """
                        if docker ps -aq -f name=^${CONTAINER}\$; then
                            echo "Stopping old container..."
                            docker stop ${CONTAINER} || true
                            docker rm ${CONTAINER} || true
                            echo "✅ Old container removed"
                        else
                            echo "ℹ️ No existing container"
                        fi
                    """
                }
            }
        }

        stage('Verify Environment File') {
            steps {
                script {
                    sh """
                        if [ ! -f ${ENV_FILE} ]; then
                            echo "❌ ENV file missing at ${ENV_FILE}"
                            exit 1
                        fi
                        echo "✅ ENV file found"
                    """
                }
            }
        }

        stage('Run New Container') {
            steps {
                script {
                    sh """
                        docker run -d \
                        --name ${CONTAINER} \
                        --network ${NETWORK} \
                        --ip ${STATIC_IP} \
                        --env-file ${ENV_FILE} \
                        -p ${PORT}:${PORT} \
                        --restart unless-stopped \
                        ${IMAGE}
                    """
                    echo "🚀 New container started"
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    sh """
                        echo "⏳ Waiting for backend to start..."
                        sleep 5

                        if docker ps -q -f name=${CONTAINER} -f status=running; then
                            echo "✅ Backend container running"
                            docker logs --tail 20 ${CONTAINER}
                        else
                            echo "❌ Backend failed to start"
                            docker logs ${CONTAINER}
                            exit 1
                        fi
                    """
                }
            }
        }

        stage('Cleanup Old Images') {
            steps {
                sh """
                    docker image prune -f || true
                    echo "🧹 Cleanup done"
                """
            }
        }
    }

    post {
        success {
            echo "🎉 Backend deployed successfully!"
            echo "Container: ${CONTAINER}"
            echo "IP: ${STATIC_IP}"
            echo "Port: ${PORT}"
        }
        failure {
            echo "❌ Deployment failed! Fetching container logs..."
            sh "docker logs ${CONTAINER} || true"
        }
        always {
            echo "Pipeline complete."
        }
    }
}
