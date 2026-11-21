package com.billbook.app.autobill

import org.junit.Assert.*
import org.junit.Test

class AutoBillScreenSessionTest {
    private val pkg = AutoBillParser.WECHAT_PKG
    private val activity = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"

    @Test fun customWechatWidgetDoesNotEraseThePaymentPage() {
        val session = AutoBillScreenSession()
        val page = session.observe(pkg, activity, 10, true, 100)
        assertEquals(page, session.observe(pkg, "com.tencent.mm.ui.widget.MMFrameLayout", 10, false, 200))
        assertTrue(session.matches(pkg, 10))
    }

    @Test fun navigatingToARealChatActivityClearsThePaymentPage() {
        val session = AutoBillScreenSession()
        session.observe(pkg, activity, 10, true, 100)
        assertNull(session.observe(pkg, "com.tencent.mm.ui.LauncherUI", 10, true, 200))
        assertFalse(session.matches(pkg, 10))
    }

    @Test fun childViewsCannotEstablishPaymentEvidence() {
        val session = AutoBillScreenSession()
        assertNull(session.observe(pkg, "com.tencent.mm.ui.widget.MMFrameLayout", 10, false, 100))
        assertFalse(session.matches(pkg, 10))
    }

    @Test fun differentRootWindowCannotUseStalePaymentEvidence() {
        val session = AutoBillScreenSession()
        session.observe(pkg, activity, 10, true, 100)
        session.observe(pkg, "android.app.Dialog", 11, false, 200)
        assertFalse(session.matches(pkg, 11))
        assertFalse(session.matches(AutoBillParser.ALIPAY_PKG, 10))
        assertTrue(session.matches(pkg, 10))
    }

    @Test fun repeatedActivityEventsKeepTheSameCaptureSession() {
        val session = AutoBillScreenSession()
        val first = session.observe(pkg, activity, 10, true, 100)
        assertEquals(first, session.observe(pkg, activity, 10, true, 200))
        assertEquals(100L, session.page!!.startedAt)
    }

    @Test fun newWindowOrReturningFromChatCreatesANewSession() {
        val session = AutoBillScreenSession()
        session.observe(pkg, activity, 10, true, 100)
        assertEquals(200L, session.observe(pkg, activity, 11, true, 200)!!.startedAt)
        session.observe(pkg, "com.tencent.mm.ui.chatting.ChattingUI", 11, true, 300)
        assertEquals(400L, session.observe(pkg, activity, 11, true, 400)!!.startedAt)
    }

    @Test fun missingWindowIdWaitsForAConcreteActivityEvent() {
        val session = AutoBillScreenSession()
        assertNull(session.observe(pkg, activity, -1, true, 100))
        assertNull(session.observe(pkg, "android.widget.FrameLayout", 10, false, 200))
        assertNotNull(session.observe(pkg, activity, 10, true, 300))
    }

    @Test fun newPaymentModuleActivityRequiresActualActivityProof() {
        val session = AutoBillScreenSession()
        // A synthetic version-compatibility fixture, not an observed device class.
        val variant = "com.tencent.mm.plugin.remittance.ui.NewTransferDetailUI"
        assertNull(session.observe(pkg, variant, 10, false, 100))
        val page = session.observe(pkg, variant, 10, true, 200)
        assertNotNull(page)
        assertTrue(session.matches(pkg, 10))
        assertEquals(200L, page!!.startedAt)
    }

    @Test fun paymentLookingCustomViewCannotReplaceVerifiedActivity() {
        val session = AutoBillScreenSession()
        val page = session.observe(pkg, activity, 10, true, 100)
        val variant = "com.tencent.mm.plugin.remittance.ui.PaymentStatusView"
        assertEquals(page, session.observe(pkg, variant, 10, false, 200))
    }

    @Test fun actualSharedRemittanceActivityCanReachTheScreenParser() {
        val session = AutoBillScreenSession()
        val sharedActivity = "com.tencent.mm.plugin.remittance.ui.RemittanceUI"
        assertFalse(AutoBillScreenParser.isPaymentActivity(pkg, sharedActivity))
        assertNull(session.observe(pkg, sharedActivity, 10, false, 100))
        assertEquals(sharedActivity, session.observe(pkg, sharedActivity, 10, true, 200)?.activity)
        assertTrue(session.matches(pkg, 10))
    }
}
