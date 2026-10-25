package org.roruku.smartformatconverter

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.data.db.AppDatabase

class RoomMigrationTest {

    @Test
    fun migration1To2HasCorrectVersions() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun migration1To2AddsExecutionTokenColumn() {
        val executedSqls = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_1_2.migrate(dbProxy)

        assertEquals(1, executedSqls.size)
        assertTrue(
            "Expected ALTER TABLE with executionToken, got: ${executedSqls[0]}",
            executedSqls[0].contains("ALTER TABLE conversion_jobs ADD COLUMN executionToken INTEGER NOT NULL DEFAULT 0")
        )
    }
}
