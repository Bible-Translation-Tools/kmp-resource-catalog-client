package org.bibletranslationtools.resourcecatalog

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

internal actual fun createDatabaseDriver(path: String): SqlDriver {
    val file = File(path)
    file.parentFile?.mkdirs()

    val driver = JdbcSqliteDriver("jdbc:sqlite:$path")

    if (!file.exists()) {
        Database.Schema.create(driver)
    } else {
        Database.Schema.migrate(
            driver = driver,
            oldVersion = 0,
            newVersion = Database.Schema.version
        )
    }

    driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
    return driver
}