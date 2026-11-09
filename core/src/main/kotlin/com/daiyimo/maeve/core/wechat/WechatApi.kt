package com.daiyimo.maeve.core.wechat

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive.Companion.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Headers
import java.io.IOException
import java.util.Base64
import kotlin.random.Random

/** 渠道版本缺失时的兜底基准地址。 */
private const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

/** 服务端有时把字符串字段写成数字（或反过来），统一按字符串读出。 */
private object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String =
        decoder.decodeSerializableValue(serializer()).content
}

@Serializable
data class WechatBaseInfo(@SerialName("channel_version") val channelVersion: String)

@Serializable
data class WechatAccount(
    val token: String,
    @SerialName("baseurl") val baseUrl: String,
    @SerialName("ilink_bot_id") val botId: String,
    @SerialName("ilink_user_id") val ownerId: String,
)

@Serializable
data class WechatQrStart(
    val ret: Int = 0,
    val errmsg: String? = null,
    val qrcode: String? = null,
    @SerialName("qrcode_img_content") val qrContent: String? = null,
)

@Serializable
data class WechatQrStatus(
    val ret: Int = 0,
    val errmsg: String? = null,
    val status: String = "wait",
    @SerialName("bot_token") val token: String? = null,
    @SerialName("ilink_bot_id") val botId: String? = null,
    @SerialName("baseurl") val baseUrl: String? = null,
    @SerialName("ilink_user_id") val ownerId: String? = null,
) {
    /** 扫码确认且拿到了完整凭据时构造账号；否则为 null。 */
    fun account(): WechatAccount? {
        if (status != "confirmed" || token.isNullOrBlank() || ownerId.isNullOrBlank()) return null
        return WechatAccount(
            token = token,
            baseUrl = baseUrl?.ifBlank { null } ?: DEFAULT_BASE_URL,
            botId = botId.orEmpty(),
            ownerId = ownerId,
        )
    }
}

@Serializable
data class WechatText(val text: String = "")

@Serializable
data class WechatItem(
    val type: Int = 0,
    @SerialName("text_item") val textItem: WechatText? = null,
)

@Serializable
data class WechatMessage(
    @Serializable(with = LenientStringSerializer::class)
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("from_user_id") val fromUserId: String? = null,
    @SerialName("to_user_id") val toUserId: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("create_time_ms") val createTimeMs: Long = 0,
    @SerialName("message_type") val messageType: Int = 0,
    @SerialName("message_state") val messageState: Int = 0,
    @SerialName("item_list") val items: List<WechatItem> = emptyList(),
    @SerialName("context_token") val contextToken: String? = null,
) {
    /** 第一版只接收用户私聊文本，其余消息不进入任务系统。 */
    fun userText(): String? {
        if (messageType != 1 || messageId.isNullOrBlank() || fromUserId.isNullOrBlank()) return null
        return items.firstOrNull { it.type == 1 }?.textItem?.text?.takeIf { it.isNotBlank() }
    }
}

@Serializable
private data class UpdatesRequest(
    @SerialName("get_updates_buf") val cursor: String,
    @SerialName("base_info") val baseInfo: WechatBaseInfo,
)

@Serializable
data class WechatUpdates(
    val ret: Int = 0,
    val errmsg: String? = null,
    val msgs: List<WechatMessage> = emptyList(),
    @SerialName("get_updates_buf") val cursor: String = "",
)

@Serializable
private data class SendRequest(
    val msg: WechatMessage,
    @SerialName("base_info") val baseInfo: WechatBaseInfo,
)

/** 业务层错误：ret 非 0。 */
class WechatApiException(val code: Int, message: String) : IOException(message)

/** 传输层错误：HTTP 状态码非 2xx。 */
class WechatHttpException(val httpCode: Int) : IOException("WeChat HTTP $httpCode")

/**
 * 微信 Bot 的 HTTPS JSON 客户端。
 *
 * 只负责协议本身，不持有任务状态，也不接触 Android 生命周期；
 * 调用方负责提供 [OkHttpClient]（可复用连接池与代理设置）。
 */
class WechatApi(
    private val client: OkHttpClient,
    private val channelVersion: String,
    private val defaultBaseUrl: String = DEFAULT_BASE_URL,
) {
    /** 申请登录二维码。 */
    fun startLogin(): WechatQrStart {
        val url = endpoint(defaultBaseUrl, "get_bot_qrcode").newBuilder()
            .addQueryParameter("bot_type", "3")
            .build()
        val request = Request.Builder()
            .url(url)
            .headers(baseHeaders())
            .post(EMPTY_BODY)
            .build()
        return execute(request, WechatQrStart.serializer())
    }

    /** 轮询扫码状态。 */
    fun pollLogin(qrcode: String): WechatQrStatus {
        val url = endpoint(defaultBaseUrl, "get_qrcode_status").newBuilder()
            .addQueryParameter("qrcode", qrcode)
            .build()
        val request = Request.Builder().url(url).headers(baseHeaders()).get().build()
        return execute(request, WechatQrStatus.serializer())
    }

    /** 拉取新消息，[cursor] 为上一次返回的游标。 */
    fun updates(account: WechatAccount, cursor: String): WechatUpdates {
        val body = UpdatesRequest(cursor, WechatBaseInfo(channelVersion))
        return post(account, "getupdates", body, UpdatesRequest.serializer(), WechatUpdates.serializer())
    }

    /** 给 [peerId] 发送一条文本消息。 */
    fun sendText(account: WechatAccount, peerId: String, clientId: String, text: String, contextToken: String?) {
        val message = WechatMessage(
            fromUserId = "",
            toUserId = peerId,
            clientId = clientId,
            messageType = 2,
            messageState = 2,
            items = listOf(WechatItem(type = 1, textItem = WechatText(text))),
            contextToken = contextToken,
        )
        val body = SendRequest(message, WechatBaseInfo(channelVersion))
        post(account, "sendmessage", body, SendRequest.serializer(), WechatQrStart.serializer())
    }

    private fun <T, R> post(
        account: WechatAccount,
        path: String,
        body: T,
        bodySerializer: SerializationStrategy<T>,
        responseSerializer: DeserializationStrategy<R>,
    ): R {
        val payload = json.encodeToString(bodySerializer, body).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(endpoint(account.baseUrl, path))
            .headers(authedHeaders(account.token))
            .post(payload)
            .build()
        return execute(request, responseSerializer)
    }

    private fun <T> execute(request: Request, serializer: DeserializationStrategy<T>): T =
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw WechatHttpException(response.code)
            if (text.isBlank()) throw IOException("WeChat returned an empty response")
            val result = json.decodeFromString(serializer, text)
            val code = when (result) {
                is WechatQrStart -> result.ret
                is WechatQrStatus -> result.ret
                is WechatUpdates -> result.ret
                else -> 0
            }
            if (code != 0) {
                val detail = (result as? WechatQrStart)?.errmsg
                    ?: (result as? WechatQrStatus)?.errmsg
                    ?: (result as? WechatUpdates)?.errmsg
                throw WechatApiException(code, detail ?: "WeChat API error $code")
            }
            result
        }

    private fun endpoint(base: String, path: String) =
        base.trimEnd('/').toHttpUrl().newBuilder().addPathSegments("ilink/bot/$path").build()

    private fun baseHeaders() = Headers.Builder()
        .add("iLink-App-Id", "bot")
        .add("iLink-App-ClientVersion", encodeVersion(channelVersion))
        .build()

    private fun authedHeaders(token: String) = Headers.Builder()
        .add("iLink-App-Id", "bot")
        .add("iLink-App-ClientVersion", encodeVersion(channelVersion))
        .add("AuthorizationType", "ilink_bot_token")
        .add("Authorization", "Bearer $token")
        .add("X-WECHAT-UIN", randomUin())
        .build()

    /** 服务端把版本号按 8 位十六进制解析，这里从原始字符串里抽数字后格式化。 */
    private fun encodeVersion(version: String): String {
        val digits = version.filter { it.isDigit() }.take(8).ifEmpty { "1" }
        return String.format("%08x", digits.toLong().toInt())
    }

    private fun randomUin(): String =
        Base64.getEncoder().encodeToString(ByteArray(4).also { Random.nextBytes(it) })

    private companion object {
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
