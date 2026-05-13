package com.x500x.cursimple.feature.schedule.time

import androidx.compose.runtime.compositionLocalOf
import com.x500x.cursimple.core.kernel.time.BeijingTime
import java.time.LocalDate
import java.time.ZoneId

val LocalAppZone = compositionLocalOf<ZoneId> { BeijingTime.zone }

fun ZoneId.today(): LocalDate = BeijingTime.todayIn(this)
