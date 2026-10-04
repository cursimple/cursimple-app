package com.x500x.cursimple.feature.schedule

import com.x500x.cursimple.core.kernel.model.MemoNote

/** 非空查询在全部笔记中按完整子串匹配；空查询恢复当前筛选，保留输入顺序和原笔记。 */
internal fun searchMemoNotes(
    memos: List<MemoNote>,
    query: String,
    currentMemos: List<MemoNote> = memos,
    courseSearchText: Map<String?, String> = emptyMap(),
): List<MemoNote> {
    val needle = query.trim()
    if (needle.isEmpty()) return currentMemos
    return memos.filter { note ->
        note.title.contains(needle, ignoreCase = true) ||
            note.body.contains(needle, ignoreCase = true) ||
            note.courseTitle.contains(needle, ignoreCase = true) ||
            note.courseKey.orEmpty().contains(needle, ignoreCase = true) ||
            courseSearchText[note.courseKey].orEmpty().contains(needle, ignoreCase = true)
    }
}
