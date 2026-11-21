package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillScreenOcrGateTest {
    @Test fun screenshotsAreSerialAndBoundedDespiteRepeatedEmptyTrees() {
        val gate = AutoBillScreenOcrGate()
        val first = gate.begin(0)!!
        assertNull(gate.begin(10_000))
        assertTrue(gate.finish(first))
        assertNull(gate.begin(2_499))
        val second = gate.begin(2_500)!!
        assertTrue(gate.finish(second))
        assertNull(gate.begin(100_000))
    }

    @Test fun anOldPageCallbackCannotFinishTheNewPagesImageRead() {
        val gate = AutoBillScreenOcrGate()
        val old = gate.begin(0)!!
        gate.clear()
        val current = gate.begin(100)!!
        assertFalse(gate.isCurrent(old))
        assertFalse(gate.finish(old))
        assertTrue(gate.isCurrent(current))
        assertTrue(gate.finish(current))
    }
}
