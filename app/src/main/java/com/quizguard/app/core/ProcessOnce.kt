package com.quizguard.app.core

/**
 * 进程级「只做一次」闸门。
 *
 * 标志只存在于内存中的单例，**不写入 SharedPreferences / 文件**，因此：
 *  - 同一个进程里，同一个 key 第一次调用 [once] 返回 true，之后一律返回 false；
 *  - 只有【关闭进程（从多任务划掉 / 强停）后重新打开 App】，单例重新创建，
 *    标志才会复位、再次允许一次。
 *
 * 用途：非 Device Owner 的普通安装下，每次调用 `startLockTask()` 系统都会弹一次
 * 「要固定屏幕吗？」确认框。陈总要求「每次启动 App 只显示一次，只有关闭进程再开才再显示」，
 * 故用本闸门控制该系统弹窗与答题页提示条的触发次数。
 *
 * 注意：系统弹窗由 Android SystemUI 弹出，第三方 App 无法直接关闭或改写它，
 * 只能减少触发次数；减少后的强制力由 GuardState 的「防逃逸守护」接管。
 */
object ProcessOnce {

    private val seen = mutableSetOf<String>()

    /** @return true = 本进程第一次请求该 key（可执行）；false = 已执行过，应跳过 */
    @Synchronized
    fun once(key: String): Boolean = seen.add(key)

    /** 该 key 在本进程是否已消费过（用于文案判断，不改变状态） */
    @Synchronized
    fun consumed(key: String): Boolean = key in seen
}
