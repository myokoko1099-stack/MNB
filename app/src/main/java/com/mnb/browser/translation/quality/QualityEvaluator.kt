package com.mnb.browser.translation.quality

/**
 * Extension point for checks that go beyond the deterministic ones, for example a later AI
 * evaluator that judges meaning. Nothing in this phase implements it with a real model. An
 * evaluator only reports issues; it must not change the input.
 */
interface QualityEvaluator {
    val id: String

    fun evaluate(input: QualityInput): List<QualityIssue>
}
