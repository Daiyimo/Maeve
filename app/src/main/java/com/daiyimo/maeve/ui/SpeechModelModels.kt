package com.daiyimo.maeve.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.daiyimo.maeve.R
import com.daiyimo.maeve.voice.SpeechChoice
import com.daiyimo.maeve.voice.DoubaoProtocol

class SpeechPickActions(
    /** 「手机自带」或「不用」:当场生效并返回。 */
    val pick: (SpeechChoice) -> Unit = {},
    /** (服务商 id, 模型名):先试转,通了才选上并返回;列表里点的和手填的都走这里。 */
    val pickModel: (String, String) -> Unit = { _, _ -> },
    /** 进豆包流式识别的设置页。 */
    val openDoubao: () -> Unit = {},
    /** (API Key, App ID, Access Token, 计费方式):加密存下凭据并选用;不联网。 */
    val saveDoubao: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    /** 沿用已存的凭据,换成这种计费方式并选用。 */
    val useDoubao: (String) -> Unit = {},
    val clearDoubao: () -> Unit = {},
)


private fun planName(resource: String): Int =
    if (resource == DoubaoProtocol.CONCURRENT) R.string.speech_doubao_concurrent else R.string.speech_doubao_duration
