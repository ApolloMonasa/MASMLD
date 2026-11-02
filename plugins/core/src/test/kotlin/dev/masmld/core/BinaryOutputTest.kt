package dev.masmld.core

import dev.masmld.core.binary.BinaryOutput
import org.junit.Assert.*
import org.junit.Test

class BinaryOutputTest {
    @Test fun `posix nm output accepts defined symbols and ignores undefined or malformed lines`() {
        assertEquals(mapOf("_Z3foov" to "0x1234", "DATA" to "0x42"), BinaryOutput.symbols("_Z3foov T 1234 8\nexternal U\nDATA D 42 4\nbroken line\n"))
    }
    @Test fun `symbolizer locations handle Unix Windows and missing debug info`() {
        assertEquals(BinaryOutput.Location("/src/main.c", 7, 1), BinaryOutput.location("foo\n/src/main.c:8:2\n"))
        assertEquals(BinaryOutput.Location("C:\\src\\main.cpp", 11, 0), BinaryOutput.location("C:\\src\\main.cpp:12"))
        assertNull(BinaryOutput.location("??\n??:0:0"))
    }
}
