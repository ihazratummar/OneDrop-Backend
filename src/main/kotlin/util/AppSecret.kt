package com.api.hazrat.util

import io.github.cdimascio.dotenv.dotenv

object AppSecret {
    private val dotenv = dotenv{
        ignoreIfMissing = true
    }

    val MONGO_DATABASE_NAME: String = dotenv["MONGO_DATABASE_NAME"]
    val USER_COLLECTION_NAME: String = dotenv["USER_COLLECTION_NAME"]
    val BLOOD_REQUEST_COLLECTION_NAME: String = dotenv["BLOOD_REQUEST_COLLECTION_NAME"]
    val REPORT_COLLECTION_NAME: String = dotenv["REPORT_COLLECTION_NAME"]
    val MONGO_CONNECTION_URI: String = dotenv["MONGO_CONNECTION_URI"]
    val KEY_BYTES: String = dotenv["KEY_BYTES"]
    val ALGORITHM: String = dotenv["ALGORITHM"]
    val TRANSFORMATION: String = dotenv["TRANSFORMATION"]
    val IV_SIZE: Int = dotenv["IV_SIZE"].toInt()
    val TAG_LENGTH: Int = dotenv["TAG_LENGTH"].toInt()
    val ONE_DROP_API_TOKEN: String = dotenv["ONE_DROP_API_TOKEN"]
    val DISCORD_WEBHOOK_URL: String = dotenv["DISCORD_WEBHOOK_URL"]
    val FIREBASE_KEY_PATH : String = dotenv["FIREBASE_KEY_PATH"]
}
