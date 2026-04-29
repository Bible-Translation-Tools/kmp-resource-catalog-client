package org.bibletranslationtools.resourcecatalog

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

internal actual fun createDatabaseDriver(path: String): SqlDriver {
    val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
    driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
    return driver
}