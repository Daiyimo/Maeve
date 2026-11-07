package com.daiyimo.maeve.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.daiyimo.maeve.ui.components.MaeveDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.R
import com.daiyimo.maeve.cap.web.WebProxy
import com.daiyimo.maeve.core.net.ProxyAddress
import com.daiyimo.maeve.i18n.tr
import com.daiyimo.maeve.settings.NetProxyStore
import com.daiyimo.maeve.ui.theme.MaeveTheme
import com.daiyimo.maeve.ui.theme.MaeveType

/**
 * 设置 › 网络代理 的文案(纯函数,便于测试)。地址只写协议、主机、端口,不带账号密码(这一行随时看得到)。
 * 只在设置首页这一处改;文件与计算环境页那一行只是引过来看。
 */
object NetProxyCopy {
    val TITLE: String get() = tr(R.string.net_proxy)
    val HINT: String get() = tr(R.string.net_proxy_hint)
    val INVALID: String get() = tr(R.string.net_proxy_invalid)

    /** 设置首页那一行右边:没设写「未设置」,设了写地址。 */
    fun value(proxy: String): String = NetProxyStore.display(proxy).ifEmpty { tr(R.string.settings_not_set) }

    /**
     * 那一行唯一的副标题:只在**确实配不上的时候**出现(只给容器用的写法、浏览器用不了)。
     * 平时返回 null —— 「调模型、下更新、内置浏览器都走它」是拿标题换个说法再说一遍，
     * 已经挪进标题旁的 [HINT] 气泡。
     * [browser] 为空表示还没查出浏览器这边的情况。
     */
    fun subtitle(proxy: String, browser: WebProxy.Mode?): String? {
        val address = ProxyAddress.parse(proxy)
        return when {
            address != null && !address.forApp -> tr(R.string.net_proxy_container_only)
            browser == WebProxy.Mode.Unsupported -> tr(R.string.net_proxy_browser_unsupported)
            browser == WebProxy.Mode.SocksWithCredentials -> tr(R.string.net_proxy_browser_socks_auth)
            else -> null
        }
    }
}

/** 改网络代理:一个输入框,空着表示不用;写得不对时就地说怎么写,不存。 */
@Composable
fun NetProxyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val ok = NetProxyStore.valid(text)
    MaeveDialog(
        onDismissRequest = onDismiss,
        title = { Text(NetProxyCopy.TITLE) },
        text = {
            Column {
                Text(NetProxyCopy.HINT, style = MaeveType.Caption, color = MaeveTheme.colors.ink3)
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("net-proxy-input"),
                    singleLine = true, isError = !ok, placeholder = { Text("http://127.0.0.1:7890") },
                )
                if (!ok) Text(NetProxyCopy.INVALID, Modifier.padding(top = 4.dp), style = MaeveType.Hint, color = MaeveTheme.colors.bad)
            }
        },
        confirmButton = { TextButton({ onSave(text) }, enabled = ok) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
