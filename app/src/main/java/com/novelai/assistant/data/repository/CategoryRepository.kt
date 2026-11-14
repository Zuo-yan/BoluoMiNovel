package com.novelai.assistant.data.repository

import com.novelai.assistant.data.db.CategoryDao
import com.novelai.assistant.data.db.CategoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun addCategory(name: String) =
        categoryDao.insert(CategoryEntity(id = UUID.randomUUID().toString(), name = name.trim()))

    suspend fun deleteCategory(id: String) = categoryDao.delete(id)

    suspend fun deleteCategoryByName(name: String) = categoryDao.deleteByName(name)
}
