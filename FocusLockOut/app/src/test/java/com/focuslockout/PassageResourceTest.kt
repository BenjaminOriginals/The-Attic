package com.focuslockout

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the typing-penalty content in res/values/typing_passages.xml. */
class PassageResourceTest {

    private val passages: List<String> by lazy {
        // Gradle runs unit tests with the module directory as the working directory.
        val file = File("src/main/res/values/typing_passages.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val items = document.getElementsByTagName("item")
        (0 until items.length).map { index ->
            // Undo Android string-resource escaping for apostrophes and quotes.
            items.item(index).textContent.replace("\\'", "'").replace("\\\"", "\"")
        }
    }

    @Test
    fun thereAreSeveralPassages() {
        assertTrue("passages=${passages.size}", passages.size >= 5)
    }

    @Test
    fun everyPassageIs80To100Words() {
        passages.forEach { passage ->
            val words = passage.trim().split(Regex("\\s+")).size
            assertTrue("$words words: ${passage.take(40)}", words in 80..100)
        }
    }

    @Test
    fun everyPassageIsTypeableOnAStockKeyboard() {
        passages.forEach { passage ->
            assertTrue("Non-ASCII in: ${passage.take(40)}", passage.all { it.code in 0x20..0x7E })
            assertTrue("Double spaces in: ${passage.take(40)}", !passage.contains("  "))
            assertTrue("Unescaped quote in: ${passage.take(40)}", !passage.contains('"'))
        }
    }
}
