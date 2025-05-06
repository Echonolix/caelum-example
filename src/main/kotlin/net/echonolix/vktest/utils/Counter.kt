package net.echonolix.vktest.utils

class Counter(val interval: Int) {

    private var count = 0
    var cps = 0; private set

    private var timer = FastTimer()

    fun invoke(action: ((Int) -> Unit)? = null) {
        timer = timer.passedAndReset(interval, {
            cps = count
            count = 0
            action?.invoke(cps)
        }, { count++ })
    }

}