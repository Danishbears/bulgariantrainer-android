package com.danishbears.bulgariantrainer.statistics

/** Unified read projections. Local session IDs are namespaced by mode; raw source tables stay intact. */
internal data class StatisticsAttempt(val id: Long, val mode: String, val sessionId: Long, val wordId: Long?,
    val direction: String?, val submitted: String?, val result: String, val grading: String, val createdAt: Long)
internal data class StatisticsSession(val id: Long, val mode: String, val status: String, val startedAt: Long,
    val completedAt: Long?, val total: Int)
