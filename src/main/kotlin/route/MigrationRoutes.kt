package com.api.hazrat.route

import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bson.Document
import java.time.Instant

fun Application.migrationRoutes(
    donorCollection: MongoCollection<Document>
) {
    routing {
        authenticate("auth-token") {
            route("/admin/migrate") {
                get {
                    val now = Instant.now().toEpochMilli()

                    val update = Document()
                        .append(
                            $$"$set", Document()
                                .append("lastResponseAt", null)
                                .append("lastDonationAt", null)

                        )
                        .append(
                            $$"$unset", Document()
                                .append("ghostCount", 0)
                        )

                    val result = donorCollection.updateMany(Document(), update)

                    call.respondText("Migration complete: matched=${result.matchedCount}, modified=${result.modifiedCount}")
                }
            }
        }
    }
}
