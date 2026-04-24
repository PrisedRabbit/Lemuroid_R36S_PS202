package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.swordfish.lemuroid.lib.library.db.entity.Game

class GameSearchDao(private val internalDao: Internal) {
    object CALLBACK : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            createSearchTable(db)
        }
    }

    object MIGRATION : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            createSearchTable(database)
        }
    }

    object MIGRATION_9_10 : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TRIGGER IF EXISTS games_bu")
            database.execSQL("DROP TRIGGER IF EXISTS games_bd")
            database.execSQL("DROP TRIGGER IF EXISTS games_au")
            database.execSQL("DROP TRIGGER IF EXISTS games_ai")
            database.execSQL("DROP TABLE IF EXISTS fts_games")
            createSearchTable(database)
        }
    }

    fun search(query: String): PagingSource<Int, Game> =
        internalDao.rawSearch(buildSearchQuery(query))

    private fun buildSearchQuery(query: String): SupportSQLiteQuery {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return SimpleSQLiteQuery("SELECT * FROM games WHERE 0")
        }

        val lowerCaseQuery = trimmedQuery.lowercase()
        val likeQuery = "%$lowerCaseQuery%"
        val prefixQuery = "$lowerCaseQuery%"
        val ftsQuery = buildFtsQuery(trimmedQuery)

        val sql = buildString {
            append("SELECT games.* FROM games WHERE ")

            if (ftsQuery != null) {
                append("games.id IN (SELECT docid FROM fts_games WHERE fts_games MATCH ?) OR ")
            }

            append(
                """
                lower(games.title) LIKE ?
                    OR lower(games.fileName) LIKE ?
                    ORDER BY
                        CASE
                            WHEN lower(games.title) = ? OR lower(games.fileName) = ? THEN 0
                            WHEN lower(games.title) LIKE ? THEN 1
                            WHEN lower(games.fileName) LIKE ? THEN 2
                            ELSE 3
                        END,
                        title ASC,
                        id DESC
                """.trimIndent()
            )
        }

        val args = buildList<Any> {
            ftsQuery?.let { add(it) }
            add(likeQuery)
            add(likeQuery)
            add(lowerCaseQuery)
            add(lowerCaseQuery)
            add(prefixQuery)
            add(prefixQuery)
        }.toTypedArray()

        return SimpleSQLiteQuery(sql, args)
    }

    private fun buildFtsQuery(query: String): String? {
        val tokens = query
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return null
        }

        return tokens.joinToString(" ") { "${escapeFtsToken(it)}*" }
    }

    private fun escapeFtsToken(token: String): String {
        return token.replace("\"", "\"\"")
    }

    @Dao
    interface Internal {
        @RawQuery(observedEntities = [(Game::class)])
        fun rawSearch(query: SupportSQLiteQuery): PagingSource<Int, Game>
    }

    companion object {
        private fun createSearchTable(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE VIRTUAL TABLE fts_games USING FTS4(
                  content="games",
                  title,
                  fileName);
                """
            )
            database.execSQL(
                """
                CREATE TRIGGER games_bu BEFORE UPDATE ON games BEGIN
                  DELETE FROM fts_games WHERE docid=old.id;
                END;
                """
            )
            database.execSQL(
                """
                CREATE TRIGGER games_bd BEFORE DELETE ON games BEGIN
                  DELETE FROM fts_games WHERE docid=old.id;
                END;
                """
            )
            database.execSQL(
                """
                CREATE TRIGGER games_au AFTER UPDATE ON games BEGIN
                  INSERT INTO fts_games(docid, title, fileName) VALUES(new.id, new.title, new.fileName);
                END;
                """
            )
            database.execSQL(
                """
                CREATE TRIGGER games_ai AFTER INSERT ON games BEGIN
                  INSERT INTO fts_games(docid, title, fileName) VALUES(new.id, new.title, new.fileName);
                END;
                """
            )
            database.execSQL(
                """
                INSERT INTO fts_games(docid, title, fileName) SELECT id, title, fileName FROM games;
                """
            )
        }
    }
}
