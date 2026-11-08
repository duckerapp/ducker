package com.github.kr328.clash.widget

import android.app.LocaleManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.view.View
import android.widget.RemoteViews
import com.github.kr328.clash.InternalControlActivity
import com.github.kr328.clash.HomeActivity
import com.github.kr328.clash.R
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.RemoteService
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.IRemoteService
import com.github.kr328.clash.service.remote.unwrap
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.ui.home.quickNodes
import com.github.kr328.clash.ui.proxy.NodeState
import com.github.kr328.clash.ui.settings.SettingsStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Виджеты на рабочий стол (D-147, D-150): «Кнопка» и «Переключатель» в стилях ducker, Nothing и Nothing
 * в чёрном, «Переключатель» ducker в белом и «Узлы» — кнопка и список узлов. Стиль выбирают в списке
 * виджетов лаунчера: это девять виджетов с общим кодом.
 *
 * Живут в процессе интерфейса, как плитка в шторке. Обновляются событиями
 * службы — включилась, выключилась, сменился узел; таймера нет.
 *
 * Кнопка ведёт в закрытую `InternalControlActivity` через неизменяемый PendingIntent: она открывает
 * приложение, если Android ещё не дал разрешение на VPN. Выбор узла приходит рассылкой сюда же.
 */
abstract class DuckerWidget(
    /** Разметка «Узлов» и их строк; `null` — кнопка или переключатель. */
    private val nodes: Nodes?,
    /** Картинка кнопки при включённом и выключенном VPN — в ней и весь стиль. */
    private val buttonOn: Int,
    private val buttonOff: Int,
) : AppWidgetProvider() {
    /**
     * `running` — служба запущена; имени профиля ещё нет, пока он грузится. `nodes == null` —
     * узлы не спрашивали, список не трогаем. Узлы — те же, что под кнопкой на главной.
     */
    private class State(
        val running: Boolean,
        val profile: String?,
        val group: String = "",
        val now: String = "",
        val nodes: List<Pair<String, Int>>? = null,
    )

    // Системное обновление приходит и после обновления приложения — так приёмник событий
    // включается у тех, кто поставил виджет на прежней сборке.
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetEvents.sync(context)

        refresh(context, true)
    }

    override fun onEnabled(context: Context) = WidgetEvents.sync(context)

    override fun onDisabled(context: Context) = WidgetEvents.sync(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SELECT -> select(context, intent)
            // О запуске служба сообщает до загрузки профиля: ядро ещё занято, узлы спросим
            // по «профиль загружен».
            Intents.ACTION_CLASH_STARTED -> refresh(context, false)
            Intents.ACTION_CLASH_STOPPED, Intents.ACTION_SERVICE_RECREATED,
            Intents.ACTION_PROFILE_LOADED, Intents.ACTION_SELECTOR_CHANGED -> refresh(context, true)
            else -> super.onReceive(context, intent)
        }
    }

    /**
     * Обновления одного вида виджета идут строго по очереди, в порядке событий: иначе позднее
     * закончившееся старое обновление рисовало поверх нового «выключено» (D-147). Очередь за
     * замком занимается прямо в `onReceive` (`UNDISPATCHED`), до первого переключения потока.
     */
    private fun refresh(context: Context, withNodes: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))

        // События службы приходят всем виджетам, а поставлен обычно один-два.
        if (ids.isEmpty()) return

        val pending = goAsync()
        val lock = locks.getOrPut(javaClass) { Mutex() }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                lock.withLock { withContext(Dispatchers.IO) { update(context, manager, ids, withNodes) } }
            } catch (e: Exception) {
                // Ошибка одного обновления не должна ронять процесс интерфейса.
                Log.w("Widget update: $e", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun update(context: Context, manager: AppWidgetManager, ids: IntArray, withNodes: Boolean) {
        val (running, profile) = StatusClient(context).status()

        if (nodes == null || !running) {
            val state = State(running, profile, nodes = emptyList())

            ids.forEach { manager.updateAppWidget(it, render(context, it, state)) }

            return
        }

        // Шапка — сразу, вместе с кнопками: список ниже может задержаться или не прийти.
        val header = State(running, profile)

        ids.forEach { manager.partiallyUpdateAppWidget(it, render(context, it, header)) }

        if (!withNodes) return

        // Вызов ядра блокирующий, прервать его нельзя — поэтому не прерываем, а перестаём ждать.
        val state = withTimeoutOrNull(QUERY_TIMEOUT) { scope.async { loadNodes(context, header) }.await() } ?: return

        ids.forEach { manager.updateAppWidget(it, render(context, it, state)) }
    }

    private suspend fun loadNodes(context: Context, header: State): State {
        val (name, group) = withClash(context) { clash ->
            clash.queryProxyGroupNames(true).firstOrNull()?.let { it to clash.queryProxyGroup(it, ProxySort.Default) }
        } ?: return State(header.running, header.profile, nodes = emptyList())

        // Ядро помнит замеры, только пока загружено; прошлые знает хранилище главной (D-72).
        val stored = DelayStore(context).all()
        val favorites = SettingsStore(context).favorites

        val all = group.proxies.map {
            NodeState(it.name, it.type, DelayStore.shown(it.delay, stored[it.name]), it.name in favorites)
        }

        return State(
            running = header.running,
            profile = header.profile,
            group = name,
            now = group.now,
            // Как на главной (D-116): группы с автовыбором, избранные, потом по порядку «Прокси».
            nodes = quickNodes(all, group.now, NODES).map { it.name to it.delay },
        )
    }

    private fun select(context: Context, intent: Intent) {
        val group = intent.getStringExtra(EXTRA_GROUP) ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: return

        val pending = goAsync()

        // Перерисует виджет рассылка о смене узла, которую шлёт служба.
        scope.launch {
            try {
                withTimeoutOrNull(QUERY_TIMEOUT) { scope.async { withClash(context) { it.patchSelector(group, name) } }.await() }
            } catch (e: Exception) {
                Log.w("Widget select: $e", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Лаунчер не пересоздаёт виджет, а применяет новое поверх прежнего вида, поэтому каждое
     * меняющееся поле задаётся явно: незаданное осталось бы от прошлого состояния. Тексты —
     * из кода, а не из разметки: разметку лаунчер читает на языке системы, а не приложения.
     */
    private fun render(context: Context, id: Int, state: State): RemoteViews {
        val on = state.running
        val text = context.localized()
        val views = RemoteViews(context.packageName, nodes?.layout ?: R.layout.widget_button)

        views.setImageViewResource(R.id.widget_toggle, if (on) buttonOn else buttonOff)

        val toggle = Intent(Intents.ACTION_TOGGLE_CLASH)
            .setClass(context, InternalControlActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

        views.setOnClickPendingIntent(
            R.id.widget_toggle,
            PendingIntent.getActivity(context, 0, toggle, pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)),
        )

        val style = nodes ?: return views

        views.setTextViewText(
            R.id.widget_status,
            text.getText(if (on) R.string.hero_protected else R.string.hero_off),
        )
        views.setTextViewText(R.id.widget_title, state.profile ?: context.applicationInfo.loadLabel(context.packageManager))
        views.setTextViewText(R.id.widget_empty, text.getText(R.string.widget_nodes_off))

        views.setOnClickPendingIntent(
            R.id.widget_header,
            PendingIntent.getActivity(
                context, 0, HomeActivity::class.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
            ),
        )

        val list = state.nodes ?: return views

        // Не коллекция, а обычные строки: коллекцию лаунчер ColorOS после обновления пакета не
        // восстанавливал, а Android 10 не отправлял из неё нажатия (F-174). Узлов всего пять.
        views.removeAllViews(R.id.widget_list)
        views.setViewVisibility(R.id.widget_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)

        list.forEachIndexed { index, (name, delay) ->
            val row = RemoteViews(context.packageName, style.row)

            row.setTextViewText(R.id.node_name, name)
            row.setTextViewText(
                R.id.node_delay,
                when {
                    delay >= DelayStore.SLOW -> text.getString(R.string.delay_slow, delay / 1000)
                    delay > 0 -> text.getString(R.string.delay_ms, delay)
                    delay == DelayStore.NO_ANSWER -> text.getString(R.string.delay_failed)
                    else -> ""
                },
            )
            row.setInt(R.id.node_row, "setBackgroundResource", if (name == state.now) style.selected else 0)

            // Foreground queue, and a new record each time so the flag is not lost to an old one (D-435).
            val select = Intent(context, javaClass)
                .setAction(ACTION_SELECT)
                .putExtra(EXTRA_GROUP, state.group)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

            // Код запроса свой у каждой строки каждого виджета: намерения различаются только
            // дополнениями, и с общим кодом система отдала бы всем строкам одно и то же.
            row.setOnClickPendingIntent(
                R.id.node_row,
                PendingIntent.getBroadcast(
                    context, id * NODES + index, select, pendingIntentFlags(PendingIntent.FLAG_CANCEL_CURRENT),
                ),
            )

            views.addView(R.id.widget_list, row)
        }

        return views
    }

    /**
     * Язык приложения (Android 13+ хранит его сам): процесс, поднятый ради рассылки, получает
     * язык системы, и виджет выходил то русским, то английским.
     */
    private fun Context.localized(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this

        val locales = getSystemService(LocaleManager::class.java).applicationLocales

        if (locales.isEmpty) return this

        return createConfigurationContext(Configuration(resources.configuration).apply { setLocales(locales) })
    }

    /**
     * Своя короткая привязка к службе: общая (`Remote.service`) держится, только пока
     * интерфейс на экране, а виджет работает и без него.
     */
    private suspend fun <T> withClash(context: Context, block: (IClashManager) -> T): T {
        val app = context.applicationContext
        val binder = CompletableDeferred<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                binder.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        check(app.bindService(RemoteService::class.intent, connection, Context.BIND_AUTO_CREATE)) { "bind failed" }

        try {
            return withTimeout(5_000) { block(binder.await().unwrap(IRemoteService::class).clash()) }
        } finally {
            app.unbindService(connection)
        }
    }

    companion object {
        /** Узлов в списке: две группы с автовыбором и три узла, если избранных нет. */
        private const val NODES = 5

        /** Сколько ждать службу: обновление держит рассылку, и дольше ждать нельзя. */
        private const val QUERY_TIMEOUT = 4_000L

        /** Своя область: у общей `Global` нет `SupervisorJob`, и одна ошибка уронила бы процесс. */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val locks = ConcurrentHashMap<Class<*>, Mutex>()

        private const val ACTION_SELECT = "com.github.kr328.clash.widget.SELECT"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_NAME = "name"
    }
}

class ButtonWidget : DuckerWidget(null, R.drawable.widget_button_ducker_on, R.drawable.widget_button_ducker_off)

class ButtonNothingWidget : DuckerWidget(null, R.drawable.widget_button_nothing_on, R.drawable.widget_button_nothing_off)

class SwitchWidget : DuckerWidget(null, R.drawable.widget_switch_ducker_on, R.drawable.widget_switch_ducker_off)

/** ducker в белом: белая дорожка и чёрная ручка при любой теме телефона (D-150). */
class SwitchWhiteWidget : DuckerWidget(null, R.drawable.widget_switch_white_on, R.drawable.widget_switch_white_off)

class SwitchNothingWidget : DuckerWidget(null, R.drawable.widget_switch_nothing_on, R.drawable.widget_switch_nothing_off)

/** Nothing в чёрном: всегда тёмные, какая бы ни была тема телефона (D-150). */
class ButtonNothingBlackWidget :
    DuckerWidget(null, R.drawable.widget_button_nothing_black_on, R.drawable.widget_button_nothing_black_off)

class SwitchNothingBlackWidget :
    DuckerWidget(null, R.drawable.widget_switch_nothing_black_on, R.drawable.widget_switch_nothing_black_off)

class NodesWidget : DuckerWidget(
    Nodes(R.layout.widget_nodes, R.layout.widget_node, R.drawable.widget_row_ducker),
    R.drawable.widget_button_ducker_on, R.drawable.widget_button_ducker_off,
)

/** «Узлы» в чёрном исполнении: всегда тёмные, какая бы ни была тема телефона (D-147). */
class NodesBlackWidget : DuckerWidget(
    Nodes(R.layout.widget_nodes_black, R.layout.widget_node_black, R.drawable.widget_row_black),
    R.drawable.widget_button_black_on, R.drawable.widget_button_black_off,
)

/** Разметка виджета, разметка строки и плашка выбранного узла. */
class Nodes(val layout: Int, val row: Int, val selected: Int)

/**
 * События службы для виджетов (аудит, perf 2.2, F-207). Раньше на них был подписан в манифесте
 * каждый из девяти видов, и система ради доставки поднимала процесс интерфейса, даже когда
 * виджетов нет: на каждое включение, выключение, смену узла и обновление подписки. Этот приёмник
 * в манифесте выключен и включён, только пока поставлен хоть один виджет; событие он передаёт
 * тем видам, у которых виджеты есть.
 */
class WidgetEvents : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        placed(context).forEach { context.sendBroadcast(Intent(intent).setComponent(it)) }
    }

    companion object {
        private val kinds = listOf(
            ButtonWidget::class.java, ButtonNothingWidget::class.java, ButtonNothingBlackWidget::class.java,
            SwitchWidget::class.java, SwitchWhiteWidget::class.java, SwitchNothingWidget::class.java,
            SwitchNothingBlackWidget::class.java, NodesWidget::class.java, NodesBlackWidget::class.java,
        )

        private fun placed(context: Context): List<ComponentName> {
            val manager = AppWidgetManager.getInstance(context)

            return kinds.map { ComponentName(context, it) }.filter { manager.getAppWidgetIds(it).isNotEmpty() }
        }

        /** Включить приёмник, если виджеты есть, и вернуть выключенным, если их не осталось. */
        fun sync(context: Context) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, WidgetEvents::class.java),
                if (placed(context).isNotEmpty()) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
