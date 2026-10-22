package com.novelai.assistant.data.sf

/** Category and work tags are independent constraints, kept together across refresh and paging. */
data class SfBrowseFilter(val categoryId: Int = 0, val tagIds: Set<Int> = emptySet()) {
    val active get() = categoryId != 0 || tagIds.isNotEmpty()
}

data class SfCategory(val id: Int, val name: String)
