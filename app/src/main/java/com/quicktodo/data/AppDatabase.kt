package com.quicktodo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TodoEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_isArchived_dueDate_createdAt ON todos(isArchived, dueDate, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_isArchived_completedAt ON todos(isArchived, completedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_isDone_isArchived ON todos(isDone, isArchived)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE todos SET sortOrder = createdAt WHERE sortOrder = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_isArchived_sortOrder_createdAt ON todos(isArchived, sortOrder, createdAt)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todos ADD COLUMN manualOrder INTEGER DEFAULT NULL")
                db.execSQL("UPDATE todos SET manualOrder = sortOrder WHERE sortOrder != createdAt")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_isArchived_manualOrder_createdAt ON todos(isArchived, manualOrder, createdAt)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quicktodo.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
