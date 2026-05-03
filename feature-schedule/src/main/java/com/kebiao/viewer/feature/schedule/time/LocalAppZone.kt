package com.kebiao.viewer.feature.schedule.time

import androidx.compose.runtime.compositionLocalOf
import com.kebiao.viewer.core.kernel.time.BeijingTime
import java.time.LocalDate
import java.time.ZoneId

val LocalAppZone = compositionLocalOf<ZoneId> { BeijingTime.zone }

fun ZoneId.today(): LocalDate = BeijingTime.todayIn(this)
