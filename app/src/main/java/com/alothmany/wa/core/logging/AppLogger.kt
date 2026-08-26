package com.alothmany.wa.core.logging

import com.alothmany.wa.core.model.LogLevel
import com.alothmany.wa.data.local.dao.LogDao
import com.alothmany.wa.data.local.entity.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLogger @Inject constructor(
    private val dao: LogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun info(module: String, message: String) = log(LogLevel.INFO, module, message)
    fun success(module: String, message: String) = log(LogLevel.SUCCESS, module, message)
    fun warning(module: String, message: String) = log(LogLevel.WARNING, module, message)
    fun error(module: String, message: String) = log(LogLevel.ERROR, module, message)

    fun log(level: LogLevel, module: String, message: String) {
        scope.launch {
            dao.insert(LogEntity(level = level.name, module = module, message = message))
        }
    }
}
