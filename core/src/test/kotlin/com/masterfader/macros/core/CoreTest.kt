package com.masterfader.macros.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    @Test fun parsesChannelRanges() {
        assertEquals((1..9).toList(), ChannelSelection.parse("1-9").channels)
        assertEquals(listOf(1, 2, 3, 7, 12, 13), ChannelSelection.parse("1-3, 7 13-12").channels)
        assertEquals("channels 1-3, 7, 12-13", ChannelSelection.parse("1-3,7,12-13").describe())
        assertEquals("channel 5", ChannelSelection.parse("5").describe())
        assertFailsWith<IllegalArgumentException> { ChannelSelection.parse("a-3") }
        assertFailsWith<IllegalArgumentException> { ChannelSelection.parse("") }
    }

    @Test fun roundTripsConfigJson() {
        val config = AppConfig(
            macros = listOf(
                SampleMacros.chapel,
                Macro("x", "All", steps = listOf(
                    Step.OpenShow("Sunday"), Step.SetMute(ChannelSelection.range(10, 12), true),
                    Step.TapText("Main"), Step.TapPoint(0.5f, 0.5f), Step.Swipe(0f, 0f, 1f, 1f), Step.Wait(250),
                )),
            ),
        )
        val text = MacroCodec.encode(config)
        assertTrue("\"type\": \"recall_snapshot\"" in text)
        assertEquals(config, MacroCodec.decode(text))
    }

    @Test fun faderGeometryMapsChannelsAndPercent() {
        val g = FaderGeometry(firstStripX = 0.1f, stripSpacing = 0.1f, firstVisibleChannel = 1, stripsPerPage = 8, topY = 0.2f, bottomY = 0.8f)
        assertEquals(0.1f, g.stripX(1)!!, 1e-6f)
        assertEquals(0.8f, g.stripX(8)!!, 1e-6f)
        assertEquals(null, g.stripX(9))
        assertEquals(0.35f, g.yFor(75), 1e-6f)
        assertEquals(0.8f, g.yFor(0), 1e-6f)
    }

    @Test fun runnerLaunchesFirstRetriesAndStopsOnFailure() = runTest {
        val performed = mutableListOf<Step>()
        var failuresLeft = 1
        val driver = object : MixerDriver {
            override suspend fun perform(step: Step) {
                if (step is Step.RecallSnapshot && failuresLeft-- > 0) throw StepFailedException("not found")
                if (step is Step.TapText) throw StepFailedException("missing")
                performed += step
            }
        }
        val runner = MacroRunner(driver, attempts = 2, retryDelayMs = 1)
        assertTrue(runner.run(SampleMacros.chapel))
        assertEquals(listOf(Step.LaunchMasterFader) + SampleMacros.chapel.steps, performed)

        val events = mutableListOf<RunEvent>()
        val bad = Macro("b", "Bad", steps = listOf(Step.TapText("nope"), Step.Wait(1)))
        assertFalse(runner.run(bad) { events += it })
        assertEquals(RunEvent.StepFailed(1, Step.TapText("nope"), "missing"), events.last())
    }
}
