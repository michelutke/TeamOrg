package ch.teamorg.domain

import kotlin.math.ceil
import kotlin.math.max

/**
 * Billed member count for a year: max of the Dec 31 snapshot and the median of the
 * Oct 1–Dec 31 samples. Median resists remove-members-before-Dec-31 gaming while
 * forgiving one-off spikes. Even-length median rounds up.
 */
fun computeBilledCount(yearEndCount: Int, q4Samples: List<Int>): Int {
    if (q4Samples.isEmpty()) return yearEndCount
    val sorted = q4Samples.sorted()
    val median = if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        ceil((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0).toInt()
    }
    return max(yearEndCount, median)
}
