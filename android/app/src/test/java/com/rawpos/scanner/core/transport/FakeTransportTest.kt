package com.rawpos.scanner.core.transport

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeTransportTest {

    @Test
    fun `records submissions in order`() = runTest {
        val transport = FakeTransport()

        transport.submit("111")
        transport.submit("222")

        assertEquals(listOf("111", "222"), transport.submitted)
    }

    @Test
    fun `exposes an immutable view`() {
        val transport = FakeTransport()

        assertEquals(emptyList<String>(), transport.submitted)
    }
}
