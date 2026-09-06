package com.example.inventory.mbt

import com.example.inventory.infrastructure.nativebridge.VerifiedReservationNative
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals

class MbtReplayTest {
    private val parser = ItfTraceParser()
    private val mapper = ObjectMapper()

    @Test
    fun `replays every Quint-generated trace and compares every transition`() {
        val traceDirectory = Path.of(requireNotNull(System.getProperty("mbt.traceDir")))
        val seed = mapper.readTree(Files.readString(traceDirectory.resolve("generation.json")))
            .path("seed").asText()
        val traces = Files.list(traceDirectory).use { files ->
            files.filter { it.extension == "json" && it.name.endsWith(".itf.json") }
                .sorted()
                .toList()
        }
        assertEquals(100, traces.size, "Trace generator contract changed")

        val replayer = TraceReplayer(VerifiedReservationNative)
        traces.forEach { file ->
            replayer.replay(file.name, seed, parser.parse(Files.readString(file)))
        }
    }
}
