pipeline {
    agent any

    environment {
        IMAGE = "onedrop-backend:v1"
        CONTAINER = "onedrop-backend"
        NETWORK = "custom_bridge"
        STATIC_IP = "172.25.0.5"
        PORT = "9091"

        // ENV file for generic env variables
        ENV_FILE = "/home/envs/onedropbackend.env"

        // Firebase JSON file stored on host
        FIREBASE_FILE_HOST = "/home/envs/firebase/onedrop_backend.json"

        // Path visible inside the container
        FIREBASE_FILE_CONTAINER = "/app/firebase/onedrop_backend.json"
    }

    stages {

        stage('Pull Code') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: 'main']],
                    userRemoteConfigs: [[
                        url: 'https://github.com/ihazratummar/onedrop-backend.git',
                        credentialsId: 'github-creds'
                    ]]
                ])
                echo "✅ Code pulled successfully"
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    sh """
                        docker build -t ${IMAGE} .
                    """
                    echo "🐳 Docker image built successfully"
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
                            echo "🗑️ Old container removed"
                        else
                            echo "ℹ️ No existing container found"
                        fi
                    """
                }
            }
        }

        stage('Verify Environment Files') {
            steps {
                script {
                    sh """
                        if [ ! -f ${ENV_FILE} ]; then
                            echo "❌ env file missing: ${ENV_FILE}"
                            exit 1
                        fi

                        if [ ! -f ${FIREBASE_FILE_HOST} ]; then
                            echo "❌ Firebase key missing: ${FIREBASE_FILE_HOST}"
                            exit 1
                        fi

                        echo "✅ ENV & Firebase files verified"
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
                        -e FIREBASE_KEY_PATH="${FIREBASE_FILE_CONTAINER}" \
                        --env-file ${ENV_FILE} \
                        -v ${FIREBASE_FILE_HOST}:${FIREBASE_FILE_CONTAINER} \
                        -p ${PORT}:${PORT} \
                        --restart unless-stopped \
                        ${IMAGE}
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    sh """
                        echo "⏳ Waiting for backend to start..."
                        sleep 6

                        if docker ps -q -f name=${CONTAINER} -f status=running; then
                            echo "🔥 Backend is running"
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
                    docker image prune -f
                    echo "🧹 Cleanup complete"
                """
            }
        }
    }

    post {
        success {
            echo "🎉 Deployment successful!"
        }
        failure {
            echo "❌ Deployment failed… showing logs"
            sh "docker logs ${CONTAINER} || true"
        }
        always {
            echo "🚦 Pipeline finished"
        }
    }
}
