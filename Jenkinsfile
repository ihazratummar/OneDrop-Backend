pipeline {
    agent any

    environment {
        IMAGE = "onedrop-backend:v1"
        CONTAINER = "onedrop-backend"

        REDIS_CONTAINER = "onedrop-redis"

        NETWORK = "custom_bridge"
        STATIC_IP = "172.25.0.5"
        PORT = "9091"
        REDIS_PORT = "6379"

        ENV_FILE = "/home/envs/onedropbackend.env"
        FIREBASE_FILE_HOST = "/home/envs/firebase/onedrop_backend.json"
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
                    sh "docker build -t ${IMAGE} ."
                    echo "🐳 Docker image built successfully"
                }
            }
        }

        // ✅ NEW: Start Redis
        stage('Run Redis') {
            steps {
                script {
                    sh """
                        if docker ps -aq -f name=^${REDIS_CONTAINER}\$; then
                            echo "Stopping old Redis..."
                            docker stop ${REDIS_CONTAINER} || true
                            docker rm ${REDIS_CONTAINER} || true
                        fi

                        docker run -d \
                        --name ${REDIS_CONTAINER} \
                        --network ${NETWORK} \
                        -p ${REDIS_PORT}:6379 \
                        --restart unless-stopped \
                        redis:7

                        echo "🧠 Redis started"
                    """
                }
            }
        }

        stage('Stop & Remove Old Backend') {
            steps {
                script {
                    sh """
                        if docker ps -aq -f name=^${CONTAINER}\$; then
                            echo "Stopping old backend..."
                            docker stop ${CONTAINER} || true
                            docker rm ${CONTAINER} || true
                            echo "🗑️ Old backend removed"
                        else
                            echo "ℹ️ No existing backend container"
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

        stage('Run Backend Container') {
            steps {
                script {
                    sh """
                        docker run -d \
                        --name ${CONTAINER} \
                        --network ${NETWORK} \
                        --ip ${STATIC_IP} \
                        -e FIREBASE_KEY_PATH="${FIREBASE_FILE_CONTAINER}" \
                        -e REDIS_HOST="${REDIS_CONTAINER}" \
                        -e REDIS_PORT="6379" \
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
                        echo "⏳ Waiting for backend..."
                        sleep 6

                        if docker ps -q -f name=${CONTAINER} -f status=running; then
                            echo "🔥 Backend is running"
                            docker logs --tail 20 ${CONTAINER}
                        else
                            echo "❌ Backend failed"
                            docker logs ${CONTAINER}
                            exit 1
                        fi

                        echo "🔍 Checking Redis..."
                        docker exec ${REDIS_CONTAINER} redis-cli ping
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
            echo "🎉 Deployment successful with Redis!"
        }
        failure {
            echo "❌ Deployment failed… logs below"
            sh "docker logs ${CONTAINER} || true"
        }
        always {
            echo "🚦 Pipeline finished"
        }
    }
}