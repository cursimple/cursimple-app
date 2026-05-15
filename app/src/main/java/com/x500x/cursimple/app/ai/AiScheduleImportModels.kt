package com.x500x.cursimple.app.ai

import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AiImportConfig(
    val apiUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isComplete: Boolean
        get() = apiUrl.isNotBlank() && apiKey.isNotBlank()
}

@Serializable
data class AiScheduleImportPayload(
    @SerialName("schedule") val schedule: TermSchedule? = null,
    @SerialName("manualCourses") val manualCourses: List<CourseItem> = emptyList(),
)

