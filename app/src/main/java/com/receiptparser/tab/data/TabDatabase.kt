package com.receiptparser.tab.data

import android.content.Context
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [FriendEntity::class, ReceiptEntity::class, ParticipantEntity::class, LineItemEntity::class, ShareEntity::class, PaymentEntity::class], version = 1, exportSchema = true)
abstract class TabDatabase : RoomDatabase() { abstract fun dao(): TabDao }
@Module @InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): TabDatabase = Room.databaseBuilder(context, TabDatabase::class.java, "tab.db").build()
}
