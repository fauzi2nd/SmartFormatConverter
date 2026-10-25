package org.roruku.smartformatconverter

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import java.sql.DriverManager
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

    @Test
    fun migration1To2ExecutesOnRealSQLiteDatabase() {
        // Test against a real in-memory SQLite engine
        DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
            conn.createStatement().use { stmt ->
                // 1. Create real v1 conversion_jobs table
                stmt.execute("""
                    CREATE TABLE conversion_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        fileName TEXT NOT NULL,
                        inputUri TEXT NOT NULL,
                        outputUri TEXT,
                        mediaType TEXT NOT NULL,
                        inputFormat TEXT NOT NULL,
                        outputFormat TEXT NOT NULL,
                        presetName TEXT NOT NULL,
                        status TEXT NOT NULL,
                        progress REAL NOT NULL,
                        speed TEXT,
                        eta TEXT,
                        errorMessage TEXT,
                        fileSize INTEGER NOT NULL,
                        outputFileSize INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        retryCount INTEGER NOT NULL,
                        relativeFolderPath TEXT,
                        customParamsJson TEXT
                    )
                """.trimIndent())

                // 2. Insert test job in v1
                stmt.execute("""
                    INSERT INTO conversion_jobs (id, inputUri, fileName, fileSize, inputFormat, outputFormat, mediaType, presetName, status, progress, createdAt, outputFileSize, retryCount)
                    VALUES ('job-v1', 'content://test', 'test_video.mp4', 2048, 'video/mp4', 'mp4', 'VIDEO', 'Balanced', 'COMPLETED', 1.0, 1000, 1024, 0)
                """.trimIndent())

                // 3. Delegate SupportSQLiteDatabase.execSQL to the real SQLite statement
                val dbWrapper = Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java)
                ) { _, method, args ->
                    if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                        stmt.execute(args[0] as String)
                    }
                    null
                } as SupportSQLiteDatabase

                // 4. Run official Room migration
                AppDatabase.MIGRATION_1_2.migrate(dbWrapper)

                // 5. Query the migrated table on the real SQLite database and verify new column exists with default value
                stmt.executeQuery("SELECT executionToken FROM conversion_jobs WHERE id = 'job-v1'").use { rs ->
                    assertTrue("Expected migrated row to exist", rs.next())
                    assertEquals("New column executionToken should default to 0", 0L, rs.getLong("executionToken"))
                }
                stmt.executeQuery("SELECT fileName, fileSize, outputFileSize, status FROM conversion_jobs WHERE id = 'job-v1'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("test_video.mp4", rs.getString("fileName"))
                    assertEquals(2048L, rs.getLong("fileSize"))
                    assertEquals(1024L, rs.getLong("outputFileSize"))
                    assertEquals("COMPLETED", rs.getString("status"))
                }
            }
        }
    }
}
