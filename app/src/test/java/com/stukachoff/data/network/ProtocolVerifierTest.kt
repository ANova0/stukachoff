package com.stukachoff.data.network

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch

class ProtocolVerifierTest {

    /** Finds a free port on the test machine */
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test
    fun `verify returns NOT_OPEN for closed port`() {
        // Allocate a port, close the server, then verify
        val port = freePort()
        // Port is closed since the ServerSocket was closed by use{}
        val result = ProtocolVerifier.verify(port)
        assertEquals(DetectedProtocol.NOT_OPEN, result)
    }

    @Test
    fun `verify returns SOCKS5 for server responding with 0x05 0x00`() {
        val server = ServerSocket(0)
        val port = server.localPort

        // verify() calls isPortReachable (connection 1), then trySocks5 (connection 2).
        // We need to handle both connections, responding with SOCKS5 bytes each time.
        val serverReady = CountDownLatch(1)
        Thread {
            runCatching {
                server.use { srv ->
                    serverReady.countDown() // signal server is ready
                    repeat(2) {
                        runCatching {
                            srv.accept().use { client ->
                                val input = client.getInputStream()
                                val output = client.getOutputStream()
                                // Read whatever arrives (reachability probe sends nothing; SOCKS5 probe sends 3 bytes)
                                val buf = ByteArray(8)
                                runCatching { input.read(buf) }
                                output.write(byteArrayOf(0x05, 0x00)) // valid SOCKS5 response
                                output.flush()
                            }
                        }
                    }
                }
            }
        }.start()

        serverReady.await() // wait for server to be listening
        val result = ProtocolVerifier.verify(port)
        assertEquals(DetectedProtocol.SOCKS5, result)
    }

    @Test
    fun `verify returns HTTP_PROXY for server responding with HTTP slash`() {
        val server = ServerSocket(0)
        val port = server.localPort

        // verify() calls: isPortReachable (conn 1), trySocks5 (conn 2, gets HTTP response → 0x05 check fails),
        // tryHttpProxy (conn 3, gets HTTP response → startsWith("HTTP/") → true).
        // We need to handle all 3 connections.
        val serverReady = CountDownLatch(1)
        Thread {
            runCatching {
                server.use { srv ->
                    serverReady.countDown() // signal server is ready
                    repeat(3) {
                        runCatching {
                            srv.accept().use { client ->
                                val input = client.getInputStream()
                                val output = client.getOutputStream()
                                val buf = ByteArray(256)
                                runCatching { input.read(buf) }
                                output.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                                output.flush()
                            }
                        }
                    }
                }
            }
        }.start()

        serverReady.await() // wait for server to be listening
        val result = ProtocolVerifier.verify(port)
        assertEquals(DetectedProtocol.HTTP_PROXY, result)
    }

    @Test
    fun `verify returns UNKNOWN_TCP for server that ignores handshakes`() {
        val server = ServerSocket(0)
        val port = server.localPort

        // verify() calls: isPortReachable (conn 1), trySocks5 (conn 2, gets garbage → false),
        // tryHttpProxy (conn 3, gets garbage → false) → UNKNOWN_TCP.
        val serverReady = CountDownLatch(1)
        Thread {
            runCatching {
                server.use { srv ->
                    serverReady.countDown() // signal server is ready
                    repeat(3) {
                        runCatching {
                            srv.accept().use { client ->
                                val input = client.getInputStream()
                                val output = client.getOutputStream()
                                val buf = ByteArray(256)
                                runCatching { input.read(buf) }
                                output.write("GARBAGE RESPONSE\r\n".toByteArray())
                                output.flush()
                            }
                        }
                    }
                }
            }
        }.start()

        serverReady.await() // wait for server to be listening
        val result = ProtocolVerifier.verify(port)
        assertEquals(DetectedProtocol.UNKNOWN_TCP, result)
    }
}
