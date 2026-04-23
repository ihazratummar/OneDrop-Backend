pipeline {
agent any

```
environment {
    IMAGE = "onedrop-backend:v1"
    CONTAINER = "onedrop-backend"

    REDIS_CONTAINER = "onedrop-redis"

    NETWORK = "custom_bridge"
    STATIC_IP = "172.25.0.5"
    PORT = "9091"

    ENV_FILE = "/home/envs/onedropbackend.env"
    FIREBASE_FILE_HOST = "/home/envs/firebase/onedrop_backend.json"
    FIREBASE_FILE_CONTAINER = "/app/firebase/onedrop_backend.json"

    // 🔥 Disable BuildKit (reduces memory spikes)
    DOCKER_BUILDKIT = "0"
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

    // ✅ Build Gradle FIRST (outside Docker)
    stage('Build App (Gradle)') {
        steps {
            sh """
                echo "⚙️ Building with Gradle..."
                ./gradlew clean build --no-daemon
            """
        }
    }

    // ✅ Controlled Docker build
    stage('Build Docker Image') {
        steps {
            script {
                sh """
                    echo "🐳 Building Docker image (limited memory)..."
                    docker build \
                    --memory=2g \
                    --memory-swap=3g \
                    -t ${IMAGE} .
                """
            }
        }
    }

    stage('Run Redis') {
        steps {
            script {
                sh """
                    if docker ps -aq -f name=^${REDIS_CONTAINER}\$; then
                        docker stop ${REDIS_CONTAINER} || true
                        docker rm ${REDIS_CONTAINER} || true
                    fi

                    docker run -d \
                    --name ${REDIS_CONTAINER} \
                    --network ${NETWORK} \
                    --restart unless-stopped \
                    redis:7

                    sleep 5

                    docker exec ${REDIS_CONTAINER} redis-cli ping
                """
            }
        }
    }

    stage('Stop Old Backend') {
        steps {
            script {
                sh """
                    docker stop ${CONTAINER} || true
                    docker rm ${CONTAINER} || true
                """
            }
        }
    }

    stage('Verify Env') {
        steps {
            sh """
                test -f ${ENV_FILE}
                test -f ${FIREBASE_FILE_HOST}
                echo "✅ ENV OK"
            """
        }
    }

    stage('Run Backend') {
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
            sh """
                sleep 5
                docker ps | grep ${CONTAINER}
                docker exec ${REDIS_CONTAINER} redis-cli ping
            """
        }
    }

    stage('Cleanup') {
        steps {
            sh "docker image prune -f"
        }
    }
}

post {
    success {
        echo "🎉 Deployment successful"
    }
    failure {
        echo "❌ Deployment failed"
        sh "docker logs ${CONTAINER} || true"
    }
}
```

}
