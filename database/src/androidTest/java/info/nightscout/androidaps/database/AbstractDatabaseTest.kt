package info.nightscout.androidaps.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Before

abstract class AbstractDatabaseTest {

    lateinit var context: Context
    lateinit var database: AppDatabase

    @Before
    fun initDatabase() {
        if (!::context.isInitialized) context = ApplicationProvider.getApplicationContext()
        if (::database.isInitialized) database.close()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

}