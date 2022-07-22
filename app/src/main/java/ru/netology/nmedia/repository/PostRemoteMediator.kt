package ru.netology.nmedia.repository

import androidx.paging.*
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.error.ApiError

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val service: ApiService,
    private val postDao : PostDao,
) : RemoteMediator<Int, PostEntity>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, PostEntity>): MediatorResult {
        try {
            val response = when (loadType) {
                LoadType.REFRESH -> service.getLatest(state.config.pageSize)
                LoadType.PREPEND -> {
                    // ранний выход если условие не выполнилось
                    val id = state.firstItemOrNull()?.id ?: return MediatorResult.Success(false)
                    service.getAfter(id, state.config.pageSize)
                }
                LoadType.APPEND -> {
                    // ранний выход если условие не выполнилось
                    val id = state.lastItemOrNull()?.id ?: return MediatorResult.Success(false)
                    service.getAfter(id, state.config.pageSize)
                }
            }

            if (!response.isSuccessful) {
                throw ApiError(response.code(), response.message())
            }
            val body = response.body() ?: throw ApiError(
                response.code(),
                response.message(),
            )
            // преобразовали список постов в postEntity
            postDao.insert(body.map(PostEntity.Companion::fromDto))

            return MediatorResult.Success(body.isEmpty())


        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}