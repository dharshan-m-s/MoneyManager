package com.moneymanager.app.ui.settings

import com.moneymanager.app.data.repository.BackupManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
    fun backupManager(): BackupManager
}
