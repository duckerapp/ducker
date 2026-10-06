package com.github.kr328.clash.common.log

object Log {
    private const val TAG = "ClashMetaForAndroid"

    /**
     * Куда ещё писать сообщения уровней info, warning и error — самописец службы (D-111).
     * В процессе интерфейса не задан. Отладочные сюда не идут: их много, и нужны они
     * только тому, кто смотрит системный журнал вживую.
     */
    @Volatile
    var sink: ((level: Char, message: String) -> Unit)? = null

    fun i(message: String, throwable: Throwable? = null): Int {
        sink?.invoke('I', withCause(message, throwable))
        return android.util.Log.i(TAG, message, throwable)
    }

    fun w(message: String, throwable: Throwable? = null): Int {
        sink?.invoke('W', withCause(message, throwable))
        return android.util.Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null): Int {
        sink?.invoke('E', withCause(message, throwable))
        return android.util.Log.e(TAG, message, throwable)
    }

    fun d(message: String, throwable: Throwable? = null) =
        android.util.Log.d(TAG, message, throwable)

    fun v(message: String, throwable: Throwable? = null) =
        android.util.Log.v(TAG, message, throwable)

    fun f(message: String, throwable: Throwable) =
        android.util.Log.wtf(TAG, message, throwable)

    private fun withCause(message: String, throwable: Throwable?): String =
        if (throwable == null) message else "$message: $throwable"
}
