package ru.netology.nmedia.repository

import androidx.paging.*
import androidx.room.withTransaction
import ru.netology.nmedia.api.ApiService
import ru.netology.nmedia.dao.PostDao
import ru.netology.nmedia.dao.PostRemoteKeyDao
import ru.netology.nmedia.db.AppDb

import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.entity.PostRemoteKeyEntity
import ru.netology.nmedia.error.ApiError

@OptIn(ExperimentalPagingApi::class)
class PostRemoteMediator(
    private val service: ApiService,
    private val postDao: PostDao,
    private val postRemoteKeyDao: PostRemoteKeyDao,
    private val appDb: AppDb
) : RemoteMediator<Int, PostEntity>() {

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, PostEntity>
    ): MediatorResult {
        try {
            val response = when (loadType) {
                LoadType.REFRESH -> service.getLatest(state.config.pageSize)
                LoadType.PREPEND -> {
                    // ранний выход если условие не выполнилось
                    val id = postRemoteKeyDao.max() ?: return MediatorResult.Success(false)
                    service.getAfter(id, state.config.pageSize)
                }
                LoadType.APPEND -> {
                    // ранний выход если условие не выполнилось
                    val id = postRemoteKeyDao.min() ?: return MediatorResult.Success(false)
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

            appDb.withTransaction {


                when (loadType) {
                    LoadType.REFRESH -> {
                        postDao.clear()
                        postRemoteKeyDao.insert(
                            listOf(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.AFTER,
                                    body.first().id
                                ),
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.BEFORE,
                                    body.last().id
                                ),
                            )
                        )
                    }
                    LoadType.PREPEND -> {
                        postRemoteKeyDao.insert(
                            listOf(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.AFTER,
                                    body.first().id
                                ),
                            )
                        )
                    }
                    LoadType.APPEND -> {
                        postRemoteKeyDao.insert(
                            listOf(
                                PostRemoteKeyEntity(
                                    PostRemoteKeyEntity.KeyType.BEFORE,
                                    body.last().id
                                ),
                            )
                        )
                    }
                }

                // преобразовали список постов в postEntity
                postDao.insert(body.map(PostEntity.Companion::fromDto))
            }
            return MediatorResult.Success(body.isEmpty())


        } catch (e: Exception) {
            return MediatorResult.Error(e)
        }
    }
}