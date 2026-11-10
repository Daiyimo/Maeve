package com.daiyimo.maeve.core.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用名单三档(取 dsh-go 应用名单的设计):支付 / 银行 / 券商默认不碰,微信每步确认,
 * 其余照常;认不出包名的不误伤。名单本身只是默认值,判定入口在 [ConfirmDecision]。
 */
class AppTierTest {
    @Test fun paymentBankingAndBrokerageAppsAreDenied() {
        for (pkg in listOf(
            "com.eg.android.AlipayGphone", "com.unionpay", "com.jd.jrapp", "cn.gov.pbc.dcep",
            "com.icbc", "cmb.pb", "com.pingan.paces.ccms",
            "com.hexin.plat.android", "com.eastmoney.android.berlin", "com.xueqiu.android", "cn.futu.trader",
        )) {
            assertEquals(pkg, AppTier.Deny, AppTiers.tierOf(pkg))
        }
    }

    @Test fun weChatIsOnTheConfirmEveryWriteList() {
        assertEquals(AppTier.ConfirmEveryWrite, AppTiers.tierOf("com.tencent.mm"))
    }

    @Test fun ordinaryAppsAreNormal() {
        // 淘宝是购物 App,不在支付 / 银行 / 券商名单里:照常
        for (pkg in listOf("com.chat", "com.tencent.mobileqq", "com.taobao.taobao", "com.unknown.app")) {
            assertEquals(pkg, AppTier.Normal, AppTiers.tierOf(pkg))
        }
    }

    @Test fun unknownPackageIsNormalNotDenied() {
        // 不知道在前的是谁:不误伤
        assertEquals(AppTier.Normal, AppTiers.tierOf(null))
        assertEquals(AppTier.Normal, AppTiers.tierOf(""))
        assertEquals(AppTier.Normal, AppTiers.tierOf("   "))
    }

    @Test fun onlyTheDenyTierIsAHardRule() {
        // 自主模式豁免软规则(每步确认),不豁免硬规则(默认不碰)
        assertTrue(AppTier.Deny.hard)
        assertTrue(!AppTier.ConfirmEveryWrite.hard)
        assertTrue(!AppTier.Normal.hard)
    }

    @Test fun theTwoListsDoNotOverlap() {
        assertTrue(AppTiers.DEFAULT_DENY.none { it in AppTiers.CONFIRM_EVERY_WRITE })
        assertTrue("com.tencent.mm" in AppTiers.CONFIRM_EVERY_WRITE)
        assertTrue("com.eg.android.AlipayGphone" in AppTiers.DEFAULT_DENY)
    }
}
