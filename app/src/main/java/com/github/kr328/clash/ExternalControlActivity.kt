package com.github.kr328.clash

import android.os.Bundle
import com.github.kr328.clash.ui.settings.SettingsStore

/**
 * Команды запуска из других приложений (Tasker и другая автоматизация): выполняются и
 * закрываются. Ссылки `ducker://install-config` принимает главный экран (D-211).
 */
class ExternalControlActivity : ControlActivity() {
    override val commandsAllowed: Boolean
        get() = SettingsStore(this).allowExternalControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        control()
    }
}
