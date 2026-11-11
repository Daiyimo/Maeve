package com.daiyimo.maeve.ui

import com.daiyimo.maeve.R
import com.daiyimo.maeve.i18n.tr
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daiyimo.maeve.MaeveApp
import com.daiyimo.maeve.core.assembly.knownTools
import com.daiyimo.maeve.browser.PageAskMode
import com.daiyimo.maeve.browser.PageAskModel
import com.daiyimo.maeve.browser.PageConversations
import com.daiyimo.maeve.browser.UserBrowser
import com.daiyimo.maeve.capsule.CapsuleModel
import com.daiyimo.maeve.capsule.CapsuleRun
import com.daiyimo.maeve.capsule.CapsuleThreads
import com.daiyimo.maeve.capsule.CardActions
import com.daiyimo.maeve.capsule.CardContent
import com.daiyimo.maeve.capsule.CardRun
import com.daiyimo.maeve.capsule.CapsuleCards
import com.daiyimo.maeve.capsule.ChatThread
import com.daiyimo.maeve.capsule.PastTurn
import com.daiyimo.maeve.chat.ConversationStore
import com.daiyimo.maeve.confirm.ConfirmationNotifier
import com.daiyimo.maeve.core.agent.TaskState
import com.daiyimo.maeve.core.cap.web.AttachedPage
import com.daiyimo.maeve.core.record.RunEvent
import com.daiyimo.maeve.core.tool.PACKAGE_NAME_RE
import com.daiyimo.maeve.takeover.Takeover
import com.daiyimo.maeve.task.TaskRun
import com.daiyimo.maeve.task.TaskService
import com.daiyimo.maeve.task.TaskService.Companion.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object PageAskCopy {
    val HINT: String get() = tr(R.string.browser_ask_hint)
    val FROM_PAGE: String get() = tr(R.string.browser_ask_from_page)
    val OPEN_CHAT: String get() = tr(R.string.browser_ask_open_chat)
    val MENU: String get() = tr(R.string.browser_ask_menu)
    val SELECTION: String get() = tr(R.string.browser_ask_selection)
    val DROP_SELECTION: String get() = tr(R.string.browser_ask_drop_selection)
    val PAUSED: String get() = tr(R.string.browser_ask_paused)
    val PAUSED_CARD: String get() = tr(R.string.browser_ask_paused_card)
    val PAUSED_HINT: String get() = tr(R.string.browser_ask_paused_hint)
    val EXPAND: String get() = tr(R.string.browser_ask_expand)
    val WEB: String get() = tr(R.string.browser_ask_web)
    val APP: String get() = tr(R.string.browser_ask_app)
    val HANDLING: String get() = tr(R.string.browser_ask_handling)
    fun continuing(title: String): String = tr(R.string.browser_ask_continuing, title)
}


class PageChat(private val context: Context, private val browser: UserBrowser, private val scope: CoroutineScope) {
    private val app = context.applicationContext as MaeveApp
    private val store = ConversationStore.shared(context)
    private val model = CapsuleModel()
    private val toolLabels by lazy { ToolLabels(knownTools()) }
    private val appLabels = HashMap<String, String>()

    var conversationId by mutableStateOf<String?>(null)
        private set
    /** 接着的那个对话的标题(给「接着「…」」那一行)。 */
    var title by mutableStateOf<String?>(null)
        private set
    /** 这一页上已经问过(这个对话是这次在这一页上开始或接上的),不再显示「接着 · 新对话」。 */
    var started by mutableStateOf(false)
        private set
    var fresh by mutableStateOf(false)
    var open by mutableStateOf(false)
    var draft by mutableStateOf("")
    var selection by mutableStateOf<String?>(null)
    /** 点了文字选择菜单里的「问 Maeve」:输入框要拿焦点(每次加一)。 */
    var focusAsk by mutableIntStateOf(0)
        private set

    var card by mutableStateOf<CardRun?>(null)
        private set
    var thread by mutableStateOf<ChatThread?>(null)
        private set
    /** 此刻那件事的步骤在哪(「网页」「App」),和 [CardContent.Working] 的步骤一一对应。 */
    var stepTags by mutableStateOf<List<String?>>(emptyList())
        private set
    private var run: TaskRun? = null
    private var past: List<PastTurn>? = null
    private var shown = CapsuleThreads.PAGE
    private var watching: Job? = null
    private var loading: Job? = null
    private var lastState: TaskState? = null
    private var exchange: Exchange? = null

    val state: TaskState? get() = card?.run?.state
    val mode: PageAskMode get() = PageAskModel.mode(state?.takeIf { it.isActive() }, open)
    val target: String? get() = if (fresh) null else conversationId

    /** 打开了一页(或换了一页):还没在这一页上问过时,按这一页找接着的对话。 */
    fun onPage(url: String?) {
        if (url == null) {
            reset()
            return
        }
        if (started) return
        scope.launch {
            val pick = withContext(Dispatchers.IO) { runCatching { PageConversations.pick(store.list(), url, System.currentTimeMillis()) }.getOrNull() }
            if (started) return@launch
            follow(pick?.id, pick?.title)
        }
    }

    /** 页面关了:回到什么都没有。 */
    private fun reset() {
        started = false
        fresh = false
        open = false
        selection = null
        follow(null, null)
    }

    /** 选中的文字要问 Maeve:带上它,输入框拿焦点。 */
    fun askAbout(text: String) {
        selection = AttachedPage.clip(text)
        focusAsk++
    }

    /** 发出输入框里的话:那件事在跑时是插话(在问你时是回答),否则在这一页上开始(带上页面说明,借出页面)。 */
    fun send() {
        val words = draft.trim()
        val sel = selection
        val live = run?.takeIf { it.state.value.isActive() && it.conversationId == target }
        if (live != null) {
            if (words.isEmpty() && sel == null) return
            if (live.state.value is TaskState.WaitingInput) TaskService.answer(context, words.ifEmpty { sel.orEmpty() }, live.key)
            else {
                // 插话里带着新选中的字:和开头一样写成页面说明(这一页此刻的样子)
                val text = sel?.let { browser.pageInfo()?.let { p -> AttachedPage.compose(p.copy(selection = it), words) } } ?: words
                TaskService.start(context, text, live.conversationId, messageId = UUID.randomUUID().toString())
            }
            draft = ""
            selection = null
            return
        }
        if (words.isEmpty() && sel == null) return
        val page = browser.pageInfo() ?: return
        val id = target ?: store.newConversation()
        val origin = PageConversations.origin(page.url, page.title)
        browser.lend(id)
        TaskService.start(context, AttachedPage.compose(page.copy(selection = sel), words), id)
        if (origin != null) scope.launch(Dispatchers.IO) { runCatching { store.setPageOrigin(id, origin) } }
        draft = ""
        selection = null
        fresh = false
        started = true
        open = true
        follow(id, title.takeIf { id == conversationId })
    }

    /** 换成跟着对话 [id]:读它记下的轮次,盯着它此刻的任务。 */
    private fun follow(id: String?, title: String?) {
        this.title = title
        if (id == conversationId && watching != null) return
        conversationId = id
        past = null
        shown = CapsuleThreads.PAGE
        card = null
        thread = null
        run = null
        exchange = null
        lastState = null
        watching?.cancel()
        if (id == null) {
            watching = null
            return
        }
        loadPast(id)
        watching = scope.launch {
            launch { store.changes.collect { if (it == id) loadPast(id) } }
            watch(id)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watch(id: String) {
        app.taskState.runs.map { runs -> runs.firstOrNull { it.conversationId == id } }.flatMapLatest { r ->
            if (r == null) flowOf(null)
            else combine(r.state, r.takeoverStage, r.activities, r.steers) { s, st, acts, steers ->
                r to CapsuleRun(r.key, r.conversationId, s, st, r.goal, exchangeOf(r, s, acts), runId = r.runId.value, onScreen = true, steers = steers)
            }
        }.collect { pair ->
            val r = pair?.first
            if (r != null) run = r
            val c = pair?.second ?: card?.run?.takeIf { !it.state.isActive() } ?: run?.let { gone ->
                // 任务已从列表里拿掉:按它最后的状态留着,等这一轮记进对话
                CapsuleRun(gone.key, gone.conversationId, gone.state.value, Takeover.Stage.None, gone.goal, exchange, runId = gone.runId.value)
            }
            val next = c?.let(model::cardRunOf)
            val before = lastState
            lastState = next?.run?.state
            card = next
            stepTags = tagsOf(next)
            PageAskModel.follow(before, lastState)?.let { open = it }
            if (before?.isActive() == true && lastState?.isActive() != true) loadPast(id)
            rebuild()
        }
    }

    private fun exchangeOf(r: TaskRun, s: TaskState, acts: List<RunEvent.Activity>): Exchange {
        val before = exchange?.takeIf { it.goal == r.goal } ?: Exchange(r.goal, emptyList(), ExchangeStatus.Working, live = true)
        return ChatViewModel.reduce(before.copy(activities = acts), s, ::appLabel, toolLabels::label).also { exchange = it }
    }

    // 每一步在哪:那一步的活动对象是「网站 …」的是网页,是包名的是 App
    private fun tagsOf(c: CardRun?): List<String?> {
        val ex = c?.run?.exchange ?: return emptyList()
        val content = CapsuleCards.content(c) as? CardContent.Working ?: return emptyList()
        val rows = ex.steps
        return content.steps.mapIndexed { i, _ ->
            val row = rows.getOrNull(i) ?: return@mapIndexed null
            val target = ex.activities.lastOrNull { it.step == row.step }?.target?.trim().orEmpty()
            when {
                target.startsWith(SITE_PREFIX) -> PageAskCopy.WEB
                PACKAGE_NAME_RE.matches(target) -> PageAskCopy.APP
                else -> null
            }
        }
    }

    private fun loadPast(id: String) {
        loading?.cancel()
        val n = shown
        loading = scope.launch {
            val (turns, name) = withContext(Dispatchers.IO) {
                runCatching { store.turns(id) to store.title(id) }.getOrDefault(emptyList<com.daiyimo.maeve.core.agent.Turn>() to null)
            }
            if (conversationId != id) return@launch
            if (name != null && title == null) title = name
            past = turns.takeLast(n.coerceAtLeast(turns.size)).map { PastTurn(it) }
            rebuild()
        }
    }

    private fun rebuild() {
        val id = conversationId ?: return
        val p = past ?: return
        val live = listOfNotNull(card).let { CapsuleThreads.liveTail(it, id, p.mapNotNull { t -> t.turn.runId }.toSet()) }
        thread = CapsuleThreads.build(p, shown, live, app.taskState.summarizing.value) { r ->
            CapsuleThreads.places(r.run.exchange?.activities.orEmpty(), ::appLabel)
        }
    }

    private fun appLabel(pkg: String): String = appLabels.getOrPut(pkg) { ConfirmationNotifier.appLabel(context, pkg) }

    /** 面板、小卡片上的按钮:都走任务服务现有的动作,带着那件事的编号。 */
    val actions = object : CardActions {
        override fun collapse() { open = false }
        override fun stop(runKey: String) = TaskService.cancel(context, runKey)
        override fun takeOver(runKey: String) = TaskService.takeOver(context, waitKey = run?.takeover?.waitKey, runKey = runKey)
        override fun resume(runKey: String) = TaskService.resume(context, runKey)
        override fun answer(runKey: String, text: String) = TaskService.answer(context, text, runKey)
        override fun approve(runKey: String, requestKey: Int) = TaskService.approve(context, requestKey, runKey)
        override fun decline(runKey: String, requestKey: Int) = TaskService.decline(context, requestKey, runKey)
        override fun openMaeve(conversationId: String) {
            runCatching { context.startActivity(TaskService.openAppIntent(context, conversationId)) }
                .onFailure { Log.w(TAG, "could not open the conversation", it) }
        }
        override fun zoom(on: Boolean) = Unit
        override fun openInput() = Unit
        override fun draft(text: String) { draft = text }
        override fun send() = this@PageChat.send()
        override fun freshChat(fresh: Boolean) { this@PageChat.fresh = fresh }
        override fun loadEarlier() {
            val id = conversationId ?: return
            if ((past?.size ?: 0) < shown) return
            shown += CapsuleThreads.PAGE
            loadPast(id)
        }
        override fun captureScreen() = Unit
        override fun dropScreen() = Unit
    }

    /** 界面测试用:不跟任何任务,直接显示 [card] 和 [thread](对话 [id])。 */
    @androidx.annotation.VisibleForTesting
    internal fun show(id: String?, card: CardRun?, thread: ChatThread?) {
        watching?.cancel()
        watching = null
        conversationId = id
        this.card = card
        this.thread = thread
        PageAskModel.follow(lastState, card?.run?.state)?.let { open = it }
        lastState = card?.run?.state
    }

    /** 交还接手的页面(「处理好了」)。 */
    fun handBack() {
        val r = run ?: return
        TaskService.handBack(context, waitKey = r.takeover?.waitKey, runKey = r.key)
    }

    /** 用户亲手摸了页面。 */
    fun touched() = browser.userTouched()

    private companion object {
        const val TAG = "MaevePageChat"
        /** 运行记录里网页那一屏的对象写成「网站 <域名>」。 */
        const val SITE_PREFIX = "网站 "
    }
}


class PageFrame(context: Context, private val onTouch: () -> Unit, private val onAsk: (done: () -> Unit) -> Unit) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) onTouch()
        return super.dispatchTouchEvent(ev)
    }

    override fun startActionModeForChild(originalView: View, callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionModeForChild(originalView, AskCallback(callback), type)

    private inner class AskCallback(private val inner: ActionMode.Callback) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = inner.onCreateActionMode(mode, menu).also { if (it) addAsk(menu) }

        // 网页内核准备菜单时会清空重排:排完再加
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = inner.onPrepareActionMode(mode, menu).also { addAsk(menu) }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != ASK_ID) return inner.onActionItemClicked(mode, item)
            // 读出选中的字之后再收起菜单(收起时选中的字可能随之取消)
            onAsk { mode.finish() }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = inner.onDestroyActionMode(mode)

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            (inner as? ActionMode.Callback2)?.onGetContentRect(mode, view, outRect) ?: super.onGetContentRect(mode, view, outRect)
        }

        private fun addAsk(menu: Menu) {
            if (menu.findItem(ASK_ID) == null) menu.add(Menu.NONE, ASK_ID, 0, PageAskCopy.MENU).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
    }

    private companion object {
        val ASK_ID = View.generateViewId()
    }
}


private const val PANEL_SHARE = 0.58f


private const val GLOW_LAYERS = 4


private val GlowColor = Color(0xFF7B5CFF)


private const val TITLE_MAX = 16


private object CardCopyText {
    val NEW_CHAT: String get() = tr(R.string.chat_new)
    val CONTINUE_LAST: String get() = tr(R.string.card_continue_last)
}


private val PanelShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)


private val CardShape = RoundedCornerShape(16.dp)


private val StepsMax = 132.dp
