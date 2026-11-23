package com.daiyimo.maeve.core.record

import kotlinx.serialization.Serializable

/** 收尾时用本次运行已读到的证据复核，范围限于这份记录能证明的内容。 */
@Serializable
data class LocalReview(
    val status: Status,
    val details: Coverage? = null,
    val claims: Coverage? = null,
    val limit: Limit? = null,
    val missing: List<String> = emptyList(),
) {
    @Serializable
    enum class Status { Verified, Partial, Insufficient, NotApplicable }

    @Serializable
    data class Coverage(val matched: Int, val total: Int)

    @Serializable
    enum class Limit { NoReceipt, UnreadChange, Truncated, NoEvidence, Unanchored, NoCheckableClaims }
}
