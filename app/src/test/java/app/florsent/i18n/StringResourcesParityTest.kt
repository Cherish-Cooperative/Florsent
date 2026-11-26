package app.florsent.i18n

import app.florsent.data.AppLanguage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * English (values/) and Traditional Chinese (values-zh-rTW/) must stay in lockstep: same keys, same
 * array lengths, same placeholders (a missing %s crashes at runtime), nothing left untranslated,
 * and the copy rule "no trailing period" holds in both.
 */
class StringResourcesParityTest {

    private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private data class Resources(
        val strings: Map<String, String>,
        val untranslatable: Set<String>,
        val arrays: Map<String, List<String>>,
        val plurals: Map<String, Map<String, String>>,
    ) {
        /** Every user-visible text, keyed for error messages. */
        val allTexts: Map<String, String>
            get() = strings +
                arrays.flatMap { (k, v) -> v.mapIndexed { i, t -> "$k[$i]" to t } } +
                plurals.flatMap { (k, v) -> v.map { (q, t) -> "$k:$q" to t } }
    }

    private fun load(dir: String): Resources {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val root = doc.documentElement
        fun elements(tag: String) = root.getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }
        fun children(e: Element, tag: String) = e.getElementsByTagName(tag).let { l -> (0 until l.length).map { l.item(it) as Element } }
        val strings = elements("string")
        return Resources(
            strings = strings.filter { it.getAttribute("translatable") != "false" }.associate { it.getAttribute("name") to it.textContent },
            untranslatable = strings.filter { it.getAttribute("translatable") == "false" }.map { it.getAttribute("name") }.toSet(),
            arrays = elements("string-array").associate { a -> a.getAttribute("name") to children(a, "item").map { it.textContent } },
            plurals = elements("plurals").associate { p ->
                p.getAttribute("name") to children(p, "item").associate { it.getAttribute("quantity") to it.textContent }
            },
        )
    }

    private val en = load("values")
    private val zh = load("values-zh-rTW")

    private fun placeholders(text: String): List<String> =
        Regex("""%(\d+\$)?[sd]""").findAll(text).map { it.value }.sorted().toList()

    private fun hasCjk(text: String) = text.any { it.code in 0x4E00..0x9FFF || it.code in 0x3000..0x30FF }

    @Test
    fun `both languages define exactly the same keys`() {
        assertEquals("strings", en.strings.keys.sorted(), zh.strings.keys.sorted())
        assertEquals("string-arrays", en.arrays.keys.sorted(), zh.arrays.keys.sorted())
        assertEquals("plurals", en.plurals.keys.sorted(), zh.plurals.keys.sorted())
        assertTrue("untranslatable strings live only in values/: ${zh.untranslatable}", zh.untranslatable.isEmpty())
        assertTrue(en.strings.size > 150)
    }

    @Test
    fun `arrays have the same number of sentences`() {
        en.arrays.forEach { (key, items) -> assertEquals(key, items.size, zh.arrays.getValue(key).size) }
        assertEquals(32, en.arrays.getValue("grounding_prompts").size)
        assertEquals(18, en.arrays.getValue("grounding_prompts_bedtime").size)
    }

    @Test
    fun `placeholders match so formatting never crashes`() {
        en.strings.forEach { (key, text) ->
            assertEquals("placeholders of $key", placeholders(text), placeholders(zh.strings.getValue(key)))
        }
        en.plurals.forEach { (key, items) ->
            val other = placeholders(zh.plurals.getValue(key).getValue("other"))
            items.forEach { (q, text) -> assertEquals("placeholders of $key:$q", other, placeholders(text)) }
        }
    }

    @Test
    fun `plurals always have an other form`() {
        (en.plurals + zh.plurals).forEach { (key, items) -> assertTrue(key, "other" in items) }
        assertTrue("English needs a singular form", "one" in en.plurals.getValue("bedtime_status_learning"))
    }

    @Test
    fun `nothing is left untranslated`() {
        // "Language / 語言" is deliberately bilingual so it can be found in either language.
        val bilingual = setOf("settings_language")
        val leftoverChinese = en.allTexts.filter { (k, v) -> k !in bilingual && hasCjk(v) }
        assertTrue("Chinese in values/: $leftoverChinese", leftoverChinese.isEmpty())
        val leftoverEnglish = zh.allTexts.filter { (_, v) ->
            val words = v.replace(Regex("""%(\d+\$)?[sd]"""), "")
            !hasCjk(words) && words.any(Char::isLetter)
        }
        assertTrue("untranslated in values-zh-rTW/: $leftoverEnglish", leftoverEnglish.isEmpty())
    }

    @Test
    fun `no trailing periods in either language`() {
        (en.allTexts + zh.allTexts.mapKeys { "zh:${it.key}" }).forEach { (key, text) ->
            val t = text.trimEnd()
            assertTrue("$key ends with a period: \"$t\"", !t.endsWith(".") && !t.endsWith("。"))
        }
    }

    @Test
    fun `the system language list matches the shipped resources`() {
        val config = File(res, "xml/locales_config.xml").readText()
        val declared = Regex("""android:name="([^"]+)"""").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("en", "zh-TW"), declared)
        assertTrue(File(res, "values-zh-rTW/strings.xml").isFile)
    }

    @Test
    fun `language tags map back to the in-app choice`() {
        assertEquals(AppLanguage.System, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.System, AppLanguage.fromTags(null))
        assertEquals(AppLanguage.TraditionalChinese, AppLanguage.fromTags("zh-TW"))
        assertEquals(AppLanguage.TraditionalChinese, AppLanguage.fromTags("zh-Hant-TW,en-US"))
        assertEquals(AppLanguage.English, AppLanguage.fromTags("en-US"))
        assertEquals("unsupported falls back to the system", AppLanguage.System, AppLanguage.fromTags("ja-JP"))
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTags(it.tag)) }
    }
}
