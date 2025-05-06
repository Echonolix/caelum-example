package net.echonolix.vktest.utils

@JvmInline
value class FastTimer(val time: Long = System.currentTimeMillis()) {
    fun passed(interval: Int): Boolean = System.currentTimeMillis() - time > interval
    inline fun passedAndRun(interval: Int, block: () -> Unit): Boolean = if (passed(interval)) {
        block()
        true
    } else false
}

inline fun FastTimer.passedAndReset(interval: Int, block: () -> Unit): FastTimer =
    if (passed(interval)) {
        block()
        FastTimer()
    } else this

inline fun FastTimer.passedAndReset(interval: Int, block: () -> Unit, block2: () -> Unit): FastTimer =
    if (passed(interval)) {
        block()
        FastTimer()
    } else {
        block2()
        this
    }