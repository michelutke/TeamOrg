package ch.teamorg.domain

import java.time.ZoneId
import java.time.ZonedDateTime

val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")

fun nextJanuaryFirstEpochSeconds(now: ZonedDateTime): Long =
    ZonedDateTime.of(now.year + 1, 1, 1, 0, 0, 0, 0, ZURICH).toEpochSecond()
