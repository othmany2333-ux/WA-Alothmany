package com.alothmany.wa.data.repository

import com.alothmany.wa.data.local.dao.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class LogRepository @Inject constructor(val dao: LogDao)
@Singleton class TaskRepository @Inject constructor(val dao: TaskDao)
@Singleton class SourceRepository @Inject constructor(val dao: SourceDao)
@Singleton class GroupRepository @Inject constructor(val dao: GroupDao)
@Singleton class LinkRepository @Inject constructor(val dao: LinkDao)
