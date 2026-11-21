package com.rawpos.scanner.core.transport

import com.rawpos.scanner.core.net.Delivery
import com.rawpos.scanner.core.net.DeliveryError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeTransportTest {

    @Test
    fun `records submissions in order`() = runTest {
        val transport = FakeTransport()

        transport.submit("111", "ean_13")
        transport.submit("222", "code_128")

        assertEquals(listOf("111", "222"), transport.submitted)
        assertEquals(listOf("ean_13", "code_128"), transport.submittedFormats)
    }

    @Test
    fun `replies with the scripted decision by default`() = runTest {
        val transport = FakeTransport()

        val delivery = transport.submit("111", "ean_13")

        assertEquals("added", (delivery as Delivery.Decided).status)
    }

    @Test
    fun `can be told to fail`() = runTest {
        val transport = FakeTransport()
        transport.fail(DeliveryError.UNCERTAIN)

        val delivery = transport.submit("111", "ean_13")

        assertEquals(DeliveryError.UNCERTAIN, (delivery as Delivery.Failed).error)
    }

    @Test
    fun `exposes an immutable view`() {
        val transport = FakeTransport()

        assertEquals(emptyList<String>(), transport.submitted)
    }
}
