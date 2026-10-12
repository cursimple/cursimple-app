package com.x500x.cursimple.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.x500x.cursimple.core.data.DataStoreManualCourseRepository
import com.x500x.cursimple.core.data.DataStoreScheduleRepository
import com.x500x.cursimple.core.data.courseReuseCatalog
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.kernel.model.CourseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Reuse metadata across timetables without switching the active term. */
@Composable
internal fun rememberCourseReuseCatalog(terms: TermProfileRepository): State<List<CourseItem>> {
    val context = LocalContext.current.applicationContext
    val catalog = remember(context, terms) {
        combine(
            DataStoreScheduleRepository(context, terms).savedSchedulesFlow,
            DataStoreManualCourseRepository(context, terms).savedCoursesFlow,
            ::courseReuseCatalog,
        ).distinctUntilChanged().flowOn(Dispatchers.IO)
    }
    return catalog.collectAsStateWithLifecycle(initialValue = emptyList())
}
