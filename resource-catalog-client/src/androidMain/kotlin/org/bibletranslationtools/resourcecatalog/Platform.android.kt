package org.bibletranslationtools.resourcecatalog

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import java.io.File

internal var appContext: Context? = null

fun initAndroid(context: Context) {
    appContext = context.applicationContext
}

internal actual fun createDatabaseDriver(path: String): SqlDriver {
    val ctx = appContext
        ?: error("Call initAndroid(context) before openLibrary()")

    val file = File(path)
    file.parentFile?.mkdirs()

    val callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
        override fun onCreate(db: SupportSQLiteDatabase) {}
        override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            super.onUpgrade(db, oldVersion, newVersion)
        }
    }

    val driver =  AndroidSqliteDriver(
        schema = Database.Schema,
        context = ctx,
        name = path,
        factory = RequerySQLiteOpenHelperFactory(),
        callback = callback
    )

    if (!file.exists()) {
        Database.Schema.create(driver)
    }

    return driver
}