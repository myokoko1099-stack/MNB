package com.mnb.browser.translation.quality

/**
 * How issues become a score and per-area statuses.
 *
 * Score = 100 minus the severity weights of all issues (CRITICAL 60, MAJOR 15, MINOR 5, INFO 0),
 * where one issue type never takes off more than [MAX_DEDUCTION_PER_TYPE] so a single repeated
 * problem cannot hide everything else. A CRITICAL issue caps the score at [CRITICAL_SCORE_CAP].
 * The score reflects structure and terminology only; it says nothing about translation accuracy.
 */
internal object QualityScoring {
    const val MAX_DEDUCTION_PER_TYPE = 60
    const val CRITICAL_SCORE_CAP = 25

    fun score(issues: List<QualityIssue>): Int {
        val deduction = issues.groupBy { it.type }.values.sumOf { group ->
            minOf(group.sumOf { it.severity.weight }, MAX_DEDUCTION_PER_TYPE)
        }
        val score = (100 - deduction).coerceIn(0, 100)
        return if (issues.any { it.severity == QualitySeverity.CRITICAL }) minOf(score, CRITICAL_SCORE_CAP) else score
    }

    /** NOT_EVALUATED when no check of the category ran; FAILED on CRITICAL/MAJOR, WARNING on MINOR, else PASSED. */
    fun status(category: QualityCategory, ran: Set<QualityCheckType>, issues: List<QualityIssue>): CheckStatus {
        if (ran.none { it.category == category }) return CheckStatus.NOT_EVALUATED
        val inCategory = issues.filter { it.category == category }
        return when {
            inCategory.any { it.severity.isBlocking } -> CheckStatus.FAILED
            inCategory.any { it.severity == QualitySeverity.MINOR } -> CheckStatus.WARNING
            else -> CheckStatus.PASSED
        }
    }
}
