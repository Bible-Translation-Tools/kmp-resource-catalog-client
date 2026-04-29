package org.bibletranslationtools.resourcecatalog

import app.cash.sqldelight.db.SqlDriver

internal expect fun createDatabaseDriver(path: String): SqlDriver
