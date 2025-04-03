package data.sqlite

import java.sql.Connection
import java.sql.DriverManager


class SqliteKtx {
    companion object {
        fun withConnection(
            dbPath: String = "./cache/VectorV2.sqlite",
            block: SqliteKtx.(connection: Connection) -> Unit
        ) {
            val sqliteDB = SqliteKtx()
            val connection: Connection = sqliteDB.connectToSQLite(dbPath)
            block.invoke(sqliteDB, connection)
            connection.close()
        }
    }

    private fun connectToSQLite(dbPath: String): Connection {
        val url = "jdbc:sqlite:$dbPath"
        return DriverManager.getConnection(url)
    }
}