package app.opora.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.data.db.CustomTagEntity
import app.opora.data.db.OporaDatabase
import app.opora.domain.TAG_TYPE_EMOTION
import app.opora.domain.TAG_TYPE_SYMPTOM
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomTagDaoTest {
    private lateinit var db: OporaDatabase

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun customTagsAreStoredAndCleared() = runBlocking {
        val dao = db.customTagDao()
        dao.upsert(CustomTagEntity("custom_1", "антистресс", TAG_TYPE_EMOTION))
        dao.insertAll(listOf(CustomTagEntity("custom_2", "тяжесть в ногах", TAG_TYPE_SYMPTOM)))

        val stored = dao.getAll().associateBy { it.key }
        assertEquals(2, stored.size)
        assertEquals("антистресс", stored.getValue("custom_1").label)
        assertEquals(TAG_TYPE_SYMPTOM, stored.getValue("custom_2").type)

        dao.clear()
        assertTrue(dao.getAll().isEmpty())
    }
}
