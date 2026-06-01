package com.example.parkingmate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities =[Vehicle::class, SavedLocation::class, ParkingSession::class], version = 10)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        // Garantisce la visibilità immediata delle modifiche della variabile tra thread diversi.
        // Evita problemi di caching locale in scenari multithread.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Restituisce l'istanza del database; se non esiste, la crea in modo safe.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parkmate_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}