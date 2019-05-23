package graphics.scenery.tests.unit

import graphics.scenery.Hub
import graphics.scenery.Settings
import graphics.scenery.utils.LazyLogger
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Tests for [Settings].
 *
 * @author Ulrik Guenther <hello@ulrik.is>
 */
class SettingsTests {
    private val logger by LazyLogger()

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

        assert(settings.get<Int>("Test.Setting") == 1337)
    }

    @Test
    fun testAddSettingIfUnsetSetting() {
        logger.info("Testing adding a setting only if unset...")
        val settings = prepareSettings()

        settings.set("Test.Setting", 1337)
        settings.setIfUnset("Test.Setting", 1338)
        assert(settings.get<Int>("Test.Setting") == 1337)

        settings.setIfUnset("Test.NewSetting", 1339)
        assert(settings.get<Int>("Test.NewSetting") == 1339)
    }

    @Test
    fun testFailOnMissingSetting() {
        logger.info("Testing failing on missing setting...")
        val settings = prepareSettings()

        assertFailsWith(IllegalStateException::class, null) {
            settings.get<Float>("Test.MissingSetting")
        }
    }

    @Test
    fun testDontFailOnMissingSettingWithDefault() {
        logger.info("Testing not failing on missing setting if default is provided...")
        val settings = prepareSettings()

        val value = settings.get("Test.MissingSetting", 1337L)
        assert(value == 1337L)
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
        assert(v == 1337L)

        settings.set("Test.Setting", 1337.0)
        val w = settings.get<Float>("Test.Setting")
        assert(w == 1337.0f)
    }
}
