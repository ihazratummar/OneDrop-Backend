package com.api.hazrat

import com.api.hazrat.route.rootRouting
import com.api.hazrat.serialization.configureSerialization
import com.api.hazrat.util.SecretConstant.ONE_DROP_API_TOKEN
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {


    val serviceAccountStreams = this::class.java.classLoader.getResourceAsStream("firebase_service_account_key.json")
        ?: throw IllegalStateException("Firebase service account file not found!")
    val options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccountStreams))
        .build()

    FirebaseApp.initializeApp(options)

    embeddedServer(Netty, port = 9091, host = "0.0.0.0") {
        authentication()
        configureSerialization()
        configureDatabases()
        rootRouting()
    }.start(wait = true)
}

fun Application.authentication(){
    install(Authentication){
        bearer ("auth-token"){
            authenticate { bearerTokenCredential ->
                if(bearerTokenCredential.token == ONE_DROP_API_TOKEN){
                    UserIdPrincipal("admin")
                }else{
                    null
                }
            }
        }
    }
}