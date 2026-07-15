package dev.gatsyuk.grindsync

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.gatsyuk.grindsync.core.database.seed.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GrindSyncApplication : Application(), Configuration.Provider {

    @Inject lateinit var seeder: DatabaseSeeder

    // Lets WorkManager construct @HiltWorker workers with injected dependencies.
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject lateinit var workoutDao: dev.gatsyuk.grindsync.core.database.dao.WorkoutDao

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { seeder.seedIfEmpty() }
        // Ongoing notification mirrors the in-progress workout (user feature).
        applicationScope.launch {
            workoutDao.observeInProgressWorkout().collect { live ->
                if (live != null) {
                    dev.gatsyuk.grindsync.core.ui.WorkoutNotifier.showActiveWorkout(
                        this@GrindSyncApplication, live.name,
                    )
                } else {
                    dev.gatsyuk.grindsync.core.ui.WorkoutNotifier.cancel(this@GrindSyncApplication)
                }
            }
        }
    }
}
