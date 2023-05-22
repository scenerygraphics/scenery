package graphics.scenery.tests.unit

import graphics.scenery.Hub
import graphics.scenery.Settings
import graphics.scenery.utils.lazyLogger
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [Settings].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class SettingsTests {
    private val logger by lazyLogger()

    private fun prepareSettings(): Settings {
        val hub = Hub()
        val settings = Settings()
        hub.add(settings)

        return settings
    }

    @Test
    fun testAddSetting() {
        logger.info("Testing adding a setting...")
        val settings = prepareSettings()
        settings.set("Test.Setting", 1337)

        assertEquals(1337, settings.get<Int>("Test.Setting"))
    }

    @Test
    fun testOverwriteSetting() {
        logger.info("Testing setting overwriting ...")
        val settings = prepareSettings()

        settings.set("Test.IntSetting", 1337)
        settings.set("Test.LongSetting", 1338L)
        settings.set("Test.FloatSetting", 1339.0f)
        settings.set("Test.DoubleSetting", 1339.0)

        assertEquals(1337, settings.set("Test.IntSetting", 1337.0f))
        assertEquals(1339.0f, settings.set("Test.FloatSetting", 1337.0))
        assertEquals(1337, settings.set("Test.IntSetting", 10000.0))
        assertEquals(10000, settings.set("Test.IntSetting", 10000.0f))

        // these should not get overwritten
        assertEquals(10000, settings.set("Test.DoubleSetting", 10000))
        assertEquals(10000, settings.set("Test.FloatSetting", 10000))
        assertEquals(10000, settings.set("Test.LongSetting", 10000))
    }

    @Test
    fun testAddSettingIfUnsetSetting() {
        logger.info("Testing adding a setting only if unset...")
        val settings = prepareSettings()

        settings.set("Test.Setting", 1337)
        settings.setIfUnset("Test.Setting", 1338)
        assertEquals(1337, settings.get<Int>("Test.Setting"))

        settings.setIfUnset("Test.NewSetting", 1339)
        assertEquals(1339, settings.get<Int>("Test.NewSetting"))
    }

    @Test
    fun testFailOnMissingSetting() {
        logger.info("Testing failing on missing setting...")
        val settings = prepareSettings()

        assertFailsWith(IllegalStateException::class, null) {
            settings.get<Float>("Test.MissingSetting")
        }

        assertFailsWith(IllegalStateException::class, null) {
            settings.getProperty<Float>("Test.MissingSetting")
        }
    }

    @Test
    fun testDontFailOnMissingSettingWithDefault() {
        logger.info("Testing not failing on missing setting if default is provided...")
        val settings = prepareSettings()

        assertEquals(1337L, settings.get("Test.MissingSetting", 1337L))
        assertEquals(1337L, settings.getProperty("Test.MissingSetting", 1337L))
    }

    @Test
    fun testFailOnWrongCast() {
        logger.info("Testing failing on impossible cast...")
        val settings = prepareSettings()
        settings.set("Test.Setting", "String")

        assertFailsWith(ClassCastException::class, null) {
            val s = settings.get<Float>("Test.Setting")
            logger.info("$s")
        }
    }

    @Test
    fun testCastingWorks() {
        logger.info("Testing casting numeric values...")
        val settings = prepareSettings()
        settings.set("Test.Setting", 1337.0f)

        val v = settings.get<Long>("Test.Setting")
        assertEquals(1337L, v)

        settings.set("Test.Setting", 1337.0)
        val w = settings.get<Float>("Test.Setting")
        assertEquals(1337.0f, w)
    }

    @Test
    fun testSettingsFromSystemProperties() {
        logger.info("Testing creation of settings from system properties")
        val nextFloat = Random.nextFloat()
        val nextInt = Random.nextInt()
        val nextLong = Random.nextLong()
        val nextBoolean = Random.nextBoolean()
        val randomString = Random.nextBytes(32).contentToString()

        System.setProperty("scenery.FloatSetting", nextFloat.toString() + "f")
        System.setProperty("scenery.IntSetting", nextInt.toString())
        System.setProperty("scenery.LongSetting", nextLong.toString() + "l")
        System.setProperty("scenery.BoolSetting", nextBoolean.toString())
        System.setProperty("scenery.StringSetting", randomString)
        val settings = prepareSettings()

        assertEquals(nextFloat, settings.get<Float>("FloatSetting"))
        assertEquals(nextInt, settings.get<Int>("IntSetting"))
        assertEquals(nextLong, settings.get<Long>("LongSetting"))
        assertEquals(nextBoolean, settings.get<Boolean>("BoolSetting"))
        assertEquals(randomString, settings.get<String>("StringSetting"))
    }

    @Test
    fun testSettingsListing() {
        logger.info("Testing settings listing as string...")
        val settings = prepareSettings()

        val vars = (0 until 10).map {
            val type = Random.nextInt(0, 5)
            Random.nextBytes(10).toString() to when(type) {
                0 -> Random.nextInt()
                1 -> Random.nextLong()
                2 -> Random.nextFloat()
                3 -> Random.nextBoolean()
                else -> Random.nextBytes(16).toString()
            }
        }

        vars.forEach { settings.set(it.first, it.second) }

        val list = settings.list()

        vars.forEach {
            assertTrue(list.contains(it.first), "List contains key ${it.first}")
            assertTrue(list.contains(it.second.toString()), "List contains value ${it.second} of key ${it.first}")
        }
    }

    @Test
    fun testSettingsList() {
        logger.info("Testing listing of settings keys ...")
        val settings = prepareSettings()

        val vars = (0 until 10).map {
            val type = Random.nextInt(0, 5)
            Random.nextBytes(10).toString() to when(type) {
                0 -> Random.nextInt()
                1 -> Random.nextLong()
                2 -> Random.nextFloat()
                3 -> Random.nextBoolean()
                else -> Random.nextBytes(16).toString()
            }
        }

        vars.forEach { settings.set(it.first, it.second) }

        val list = settings.getAllSettings()

        vars.forEach {
            assertTrue(list.contains(it.first), "List contains key ${it.first}")
        }

    }
}
