package com.github.kr328.clash.common.compat

/** Android запрещает фоновый запуск: отказ сообщаем вызывающему, процесс сохраняем. */
internal fun tryStartForegroundService(start: () -> Unit, rejected: (RuntimeException) -> Unit): Boolean {
    try {
        start()
        return true
    } catch (error: IllegalStateException) {
        rejected(error)
    } catch (error: SecurityException) {
        rejected(error)
    }
    return false
}
