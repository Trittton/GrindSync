package dev.gatsyuk.grindsync

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.gatsyuk.grindsync.core.database.seed.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GrindSyncApplication : Application() {

    @Inject lateinit var seeder: DatabaseSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seeder.seedIfEmpty() }
    }
}
