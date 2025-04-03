package data.sqlite

import org.sqlite.SQLiteConfig
import java.sql.Connection
import java.sql.DriverManager


object SqliteKtx {

    fun withConnection(
        dbPath: String = "./cache/VectorV2.sqlite",
        block: Connection.() -> Unit
    ) {
        val connection: Connection = connectToSQLite(dbPath)
        block.invoke(connection)
        connection.close()
    }

    fun Connection.executeQuery(sqlQuery: String) {
        createStatement().use { it.executeQuery(sqlQuery) }
    }

    fun Connection.executeOperation(sqlQuery: String) {
        createStatement().use { it.execute(sqlQuery) }
    }

    private fun connectToSQLite(dbPath: String): Connection {
        val config = SQLiteConfig().apply {
            enableLoadExtension(true)
        }
        val url = "jdbc:sqlite:$dbPath"
        return DriverManager.getConnection(url, config.toProperties())
    }
}