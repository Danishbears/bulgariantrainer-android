package com.danishbears.bulgariantrainer.data

import androidx.room.withTransaction

/** Reject an old operation waiting behind a full replacement, including overlapping numeric IDs. */
internal suspend fun <T> TrainerDatabase.withContentTransaction(block: suspend () -> T): T {
    val expected = contentEpoch.get()
    return withTransaction {
        check(expected == contentEpoch.get()) { "Local data was replaced; reload the screen" }
        block()
    }
}
