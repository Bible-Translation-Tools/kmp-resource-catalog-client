package org.bibletranslationtools.resourcecatalog

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

internal var appContext: Context? = null

fun initAndroid(context: Context) {
    appContext = context.applicationContext
}

internal actual fun createDatabaseDriver(path: String): SqlDriver {
    val ctx = appContext
        ?: error("Call initAndroid(context) before open()")

    val callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
        override fun onCreate(db: SupportSQLiteDatabase) {}
        override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
    }

    return AndroidSqliteDriver(
        schema = Database.Schema,
        context = ctx,
        name = path,
        factory = RequerySQLiteOpenHelperFactory(),
        callback = callback
    )
}