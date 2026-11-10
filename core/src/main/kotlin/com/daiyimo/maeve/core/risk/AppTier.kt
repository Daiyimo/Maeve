package com.daiyimo.maeve.core.risk

/**
 * 应用名单三档(取 dsh-go 应用名单的设计,不搬代码):
 *
 * - [Deny] 支付 / 银行 / 券商类:默认不碰——写操作一律不做,自主模式也不例外;
 * - [ConfirmEveryWrite] 微信:每一次写操作都要用户确认(询问时;自主模式豁免,见 [ConfirmDecision]);
 * - [Normal] 其余照常。
 */
enum class AppTier {
    Deny, ConfirmEveryWrite, Normal;

    /** 这一档是不是硬规则:任何模式下(含自主)都拦住写操作。 */
    val hard: Boolean get() = this == Deny
}

/**
 * 默认应用名单,随包发布的兜底(用户以后可在设置里逐项放开;先做默认值)。
 * 按包名认,认不出的按 [AppTier.Normal] 照常——宁可贵备,不误伤普通 App。
 */
object AppTiers {
    /** 支付、银行、券商:默认不碰。 */
    val DEFAULT_DENY: Set<String> = setOf(
        // 支付
        "com.eg.android.AlipayGphone", "com.unionpay", "com.chinatelecom.bestpayclient", "com.jd.jrapp",
        "cn.gov.pbc.dcep", "com.paypal.android.p2pmobile", "com.mybank.android.phone", "com.webank.wemoney",
        // 银行
        "com.icbc", "com.chinamworld.main", "com.android.bankabc", "com.chinamworld.bocmbci", "com.bankcomm.Bankcomm",
        "cmb.pb", "com.cmbchina.ccd.pluto.cmbActivity", "com.yitong.mbank.psbc", "cn.com.spdb.mobilebank.per",
        "cn.com.cmbc.newmbank", "com.ecitic.bank.mobile", "com.cebbank.mobile.cemb", "com.pingan.paces.ccms",
        "com.cgbchina.xpt", "com.bankofbeijing.mobilebanking",
        // 券商
        "com.hexin.plat.android", "com.eastmoney.android.berlin", "com.lphtsccft", "com.xueqiu.android",
        "cn.futu.trader", "com.tigerbrokers.stock",
    )

    /** 微信:可读,每一次写操作都要确认。 */
    val CONFIRM_EVERY_WRITE: Set<String> = setOf("com.tencent.mm")

    /** [pkg] 在哪一档;null / 空包名按 [AppTier.Normal](不知道在前的是谁,不误伤)。 */
    fun tierOf(pkg: String?): AppTier = when {
        pkg.isNullOrBlank() -> AppTier.Normal
        pkg in DEFAULT_DENY -> AppTier.Deny
        pkg in CONFIRM_EVERY_WRITE -> AppTier.ConfirmEveryWrite
        else -> AppTier.Normal
    }
}
