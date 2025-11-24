package com.api.hazrat

// DecryptAndRewriteMigration.kt
import com.api.hazrat.util.EncryptionUtil
import com.api.hazrat.util.AppSecret.BLOOD_REQUEST_COLLECTION_NAME
import com.api.hazrat.util.AppSecret.MONGO_CONNECTION_URI
import com.api.hazrat.util.AppSecret.MONGO_DATABASE_NAME
import com.api.hazrat.util.AppSecret.USER_COLLECTION_NAME
import com.mongodb.client.MongoClients
import com.mongodb.client.model.Filters.eq
import org.bson.Document

/**
 * One-time migration:
 * - Decrypts fields: age, gender, bloodGroup, city, district, state
 * - Writes plaintext back into same fields (replacing encrypted string)
 * - Leaves name, contactNumber, email encrypted
 *
 * Usage:
 *  1) Set environment variables (or edit the defaults below)
 *     - MONGO_URI (e.g. "mongodb://localhost:27017")
 *     - DB_NAME
 *     - COLLECTION_NAME
 *  2) Run with DRY_RUN=true to preview changes
 *  3) Set DRY_RUN=false to apply updates
 *
 * MAKE A BACKUP FIRST (mongodump).
 */

fun main() {
    val MONGO_URI = MONGO_CONNECTION_URI
    val DB_NAME = MONGO_DATABASE_NAME
    val COLL_NAME = USER_COLLECTION_NAME

    // Toggle this before running:
    val DRY_RUN = false

    println("Connecting to Mongo: $MONGO_URI, DB: $DB_NAME, Collection: $COLL_NAME")
    println("DRY_RUN = $DRY_RUN (set env DRY_RUN=false to apply updates)")

    val client = MongoClients.create(MONGO_URI)
    val db = client.getDatabase(DB_NAME)
    val collection = db.getCollection(COLL_NAME)

    // fields we want to decrypt and stop encrypting
    val fieldsToDecrypt = listOf("age", "gender", "bloodGroup", "city", "district", "state")

    val cursor = collection.find().batchSize(200).iterator()
    var total = 0L
    var changed = 0L
    var skipped = 0L
    var errors = 0L

    try {
        while (cursor.hasNext()) {
            val doc = cursor.next()
            total++

            val id = doc.getString("_id") ?: doc.getObjectId("_id").toString()
            val updates = Document()
            var willChange = false

            for (field in fieldsToDecrypt) {
                val rawVal = doc.getString(field)
                if (rawVal == null) {
                    // no value present; skip
                    continue
                }

                // Attempt decryption. If decrypt throws, assume rawVal is already plaintext and use as-is.
                val plainVal: String = try {
                    EncryptionUtil.decrypt(rawVal)
                } catch (e: Exception) {
                    // Could be already plaintext or corrupted ciphertext; keep original value to avoid data loss.
                    rawVal
                }

                // If decrypted/plain value differs from stored value, we plan to update
                if (plainVal != rawVal) {
                    updates.append(field, plainVal)
                    willChange = true
                } else {
                    // If decrypt returned same data (or we couldn't decrypt), but we still want to ensure field is 'plaintext' type,
                    // we do nothing — update only when decrypted plaintext differs.
                }
            }

            if (willChange) {
                if (DRY_RUN) {
                    println("[DRY_RUN] Would update _id=$id with: ${updates.toJson()}")
                    changed++
                } else {
                    try {
                        val result = collection.updateOne(eq("_id", id), Document("\$set", updates))
                        println("[APPLY] Updated _id=$id matched=${result.matchedCount} modified=${result.modifiedCount}, set=${updates.toJson()}")
                        changed++
                    } catch (ex: Exception) {
                        println("[ERROR] Failed updating _id=$id -> ${ex.message}")
                        errors++
                    }
                }
            } else {
                skipped++
            }

            if (total % 500 == 0L) {
                println("Progress: processed=$total, willChange/changed=$changed, skipped=$skipped, errors=$errors")
            }
        }

        println("Done. processed=$total, changed=$changed, skipped=$skipped, errors=$errors")
    } finally {
        cursor.close()
        client.close()
    }

    if (!DRY_RUN && errors > 0) {
        println("Migration completed with errors. Check logs.")
    }
}
