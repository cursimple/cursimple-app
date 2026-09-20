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

    /** 把学期绑到一套作息上；[timingProfileId] 为 null 时解除绑定。 */
    suspend fun setTermTimingProfile(id: String, timingProfileId: String?)

    /** 设定这个学期自己加了几周空白周；负数按 0 处理。 */
    suspend fun setTermExtraWeekCount(id: String, extraWeekCount: Int)

    /**
     * 在当前值上增减空白周数，返回改完之后的值。
     *
     * 读-改-写放在仓储里一次做完：界面上拿到的数可能已经过期（连点两下、
     * 或者写入还没回流到界面），按那个旧数去写会把前一次的改动吞掉。
     */
    suspend fun adjustActiveTermExtraWeekCount(delta: Int): Int
    suspend fun deleteTerm(id: String)
    suspend fun setActiveTerm(id: String)

    /**
     * 引导迁移：不存在任何学期时，用取自旧版全局偏好的 [legacyTermStartDateIso]
     * 创建一个名为 [defaultName] 的学期并设为活动学期。返回最终的活动学期 id。
     */
    suspend fun ensureBootstrapped(defaultName: String, legacyTermStartDateIso: String?): String
}
