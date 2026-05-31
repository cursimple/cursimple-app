package com.x500x.cursimple.app.ai

import com.x500x.cursimple.core.data.DEFAULT_AI_IMPORT_TIMEOUT_SECONDS
import com.x500x.cursimple.core.kernel.model.CourseItem
import com.x500x.cursimple.core.kernel.model.TermSchedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AiImportConfig(
    val apiUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSeconds: Int = DEFAULT_AI_IMPORT_TIMEOUT_SECONDS,
) {
    val isComplete: Boolean
        get() = apiUrl.isNotBlank() && apiKey.isNotBlank()
}

@Serializable
data class AiScheduleImportPayload(
    @SerialName("schedule") val schedule: TermSchedule? = null,
    @SerialName("manualCourses") val manualCourses: List<CourseItem> = emptyList(),
)

