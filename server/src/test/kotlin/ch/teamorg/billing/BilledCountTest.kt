package ch.teamorg.billing

import ch.teamorg.domain.computeBilledCount
import kotlin.test.Test
import kotlin.test.assertEquals

class BilledCountTest {
    @Test fun `no samples returns year-end count`() =
        assertEquals(12, computeBilledCount(12, emptyList()))

    @Test fun `year-end higher than median wins`() =
        assertEquals(20, computeBilledCount(20, listOf(15, 16, 15)))

    @Test fun `median beats gamed year-end drop`() =
        // 30 members all season, treasurer removes 25 members on Dec 30
        assertEquals(30, computeBilledCount(5, listOf(30, 30, 29, 30, 31)))

    @Test fun `median forgives one-off spike`() =
        // guest import spiked one sample to 80
        assertEquals(31, computeBilledCount(30, listOf(30, 31, 80, 31, 30)))

    @Test fun `even sample count rounds median up`() =
        assertEquals(16, computeBilledCount(0, listOf(15, 16)))

    @Test fun `zero members zero samples bills zero`() =
        assertEquals(0, computeBilledCount(0, emptyList()))
}
