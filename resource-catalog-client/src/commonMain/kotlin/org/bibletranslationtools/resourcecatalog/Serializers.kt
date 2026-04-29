package org.bibletranslationtools.resourcecatalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bibletranslationtools.resourcecontainer.IntAsStringSerializer

val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true

    serializersModule = SerializersModule {
        contextual(String::class, IntAsStringSerializer)
    }
}
