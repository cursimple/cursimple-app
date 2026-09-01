package com.x500x.cursimple.core.data.term

import kotlinx.coroutines.flow.Flow

interface TermProfileRepository {
    val termsFlow: Flow<List<TermProfile>>
    val activeTermIdFlow: Flow<String>

    /** 挂起直到活动学期加载完成。 */
    suspend fun activeTermId(): String

    suspend fun createTerm(name: String, termStartDateIso: String?): TermProfile
    suspend fun renameTerm(id: String, name: String)
    suspend fun setTermStartDate(id: String, dateIso: String?)
    suspend fun deleteTerm(id: String)
    suspend fun setActiveTerm(id: String)

    /**
     * 引导迁移：不存在任何学期时，用取自旧版全局偏好的 [legacyTermStartDateIso]
     * 创建一个名为 [defaultName] 的学期并设为活动学期。返回最终的活动学期 id。
     */
    suspend fun ensureBootstrapped(defaultName: String, legacyTermStartDateIso: String?): String
}
