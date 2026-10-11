package com.receiptparser.tab.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [FriendEntity::class, ReceiptEntity::class, ParticipantEntity::class, LineItemEntity::class, ShareEntity::class, PaymentEntity::class, RestoreStateEntity::class], version = 2, exportSchema = true)
abstract class TabDatabase : RoomDatabase() {
    abstract fun dao(): TabDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `restore_state` (`id` INTEGER NOT NULL, `settingsJson` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
@Module @InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): TabDatabase = Room.databaseBuilder(context, TabDatabase::class.java, "tab.db").addMigrations(TabDatabase.MIGRATION_1_2).build()
}
