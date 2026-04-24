pipeline {
    agent any

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

        DOCKER_BUILDKIT = "0"
    }

    stages {

        stage('Pre-build Cleanup') {
            steps {
                sh '''
                    # Drop page cache before heavy build
                    sync && echo 3 | sudo tee /proc/sys/vm/drop_caches || true
                    # Remove dangling docker layers
                    docker system prune -f --volumes || true
                    free -h
                '''
            }
        }


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
                echo "Code pulled successfully"
            }
        }

        stage('Build App (Gradle)') {
            steps {
                sh '''
                    export JAVA_OPTS="-Xmx512m -Xms256m"
                    export GRADLE_OPTS="-Xmx512m -Dorg.gradle.jvmargs=-Xmx512m"
                    ./gradlew clean build --no-daemon --max-workers=1
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                sh '''
                    # Give JVM time to fully exit before Docker build starts
                    sleep 3
                    docker build --memory=1g --memory-swap=2g -t onedrop-backend:v1 .
                '''
            }
        }

        stage('Run Redis') {
            steps {
                sh '''
                    docker stop onedrop-redis || true
                    docker rm onedrop-redis || true

                    docker run -d \
                    --name onedrop-redis \
                    --network custom_bridge \
                    --restart unless-stopped \
                    redis:7

                    sleep 5
                    docker exec onedrop-redis redis-cli ping
                '''
            }
        }

        stage('Stop Old Backend') {
            steps {
                sh '''
                    docker stop onedrop-backend || true
                    docker rm onedrop-backend || true
                '''
            }
        }

        stage('Verify Env') {
            steps {
                sh '''
                    test -f /home/envs/onedropbackend.env
                    test -f /home/envs/firebase/onedrop_backend.json
                    echo "ENV OK"
                '''
            }
        }

        stage('Run Backend') {
            steps {
                sh '''
                    docker run -d \
                    --name onedrop-backend \
                    --network custom_bridge \
                    --ip 172.25.0.5 \
                    -e FIREBASE_KEY_PATH="/app/firebase/onedrop_backend.json" \
                    -e REDIS_HOST="onedrop-redis" \
                    -e REDIS_PORT="6379" \
                    --env-file /home/envs/onedropbackend.env \
                    -v /home/envs/firebase/onedrop_backend.json:/app/firebase/onedrop_backend.json \
                    -p 9091:9091 \
                    --restart unless-stopped \
                    onedrop-backend:v1
                '''
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    sleep 5
                    docker ps | grep onedrop-backend
                    docker exec onedrop-redis redis-cli ping
                '''
            }
        }

        stage('Cleanup') {
            steps {
                sh 'docker image prune -f'
            }
        }
    }

    post {
        success {
            echo "Deployment successful"
        }
        failure {
            echo "Deployment failed"
            sh 'docker logs onedrop-backend || true'
        }
    }
}