package com.rawpos.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationParityTest {

    @Test
    fun `Arabic and English string keys are identical`() {
        val res = resourcesDir()
        val english = stringKeys(File(res, "values/strings.xml"))
        val arabic = stringKeys(File(res, "values-ar/strings.xml"))

        assertEquals(
            "Missing in Arabic: ${english - arabic}; missing in English: ${arabic - english}",
            english,
            arabic,
        )
        assertTrue("Expected a meaningful starter catalog", english.size >= 40)
    }

    @Test
    fun `No string value is blank`() {
        val res = resourcesDir()
        listOf("values/strings.xml", "values-ar/strings.xml").forEach { path ->
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(File(res, path))
            val nodes = document.getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                val name = element.getAttribute("name")
                val value = element.textContent.trim()
                assertTrue("Blank string resource: $path $name", value.isNotEmpty())
            }
        }
    }

    private fun resourcesDir(): File {
        val moduleRelative = File("src/main/res")
        if (moduleRelative.isDirectory) return moduleRelative
        val repoRelative = File("app/src/main/res")
        if (repoRelative.isDirectory) return repoRelative
        error("Cannot locate Android resources from ${File(".").absolutePath}")
    }

    private fun stringKeys(file: File): Set<String> {
        assertTrue("Missing file: ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                add(element.getAttribute("name"))
            }
        }
    }
}
