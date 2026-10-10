package dev.rafaelm.pandora.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class StatusContractTest {
    @Test
    fun `Android response model matches the generated Rust contract`() {
        val text = checkNotNull(javaClass.getResourceAsStream("/openapi.json"))
            .bufferedReader().use { it.readText() }
        val document = Json.parseToJsonElement(text).jsonObject
        val schema = document.getValue("components").jsonObject
            .getValue("schemas").jsonObject.getValue("StatusResponse").jsonObject
        val properties = schema.getValue("properties").jsonObject
        val required = schema.getValue("required").jsonArray.map { it.jsonPrimitive.content }.toSet()
        val descriptor = ServerStatus.serializer().descriptor
        val fields = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()
        val requiredFields = (0 until descriptor.elementsCount)
            .filterNot(descriptor::isElementOptional).map(descriptor::getElementName).toSet()

        assertEquals(properties.keys, fields)
        assertEquals(required, requiredFields)
        for (index in 0 until descriptor.elementsCount) {
            val name = descriptor.getElementName(index)
            assertEquals("string", properties.getValue(name).jsonObject.getValue("type").jsonPrimitive.content)
            assertEquals(PrimitiveKind.STRING, descriptor.getElementDescriptor(index).kind)
        }
    }
}
