package com.quizguard.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * 设备管理员接收器。仅在“设备所有者（Device Owner）”升级路径下使用，
 * 用于让系统把本应用加入锁屏任务白名单，实现“无法退出”的 kiosk 模式。
 * 普通钉屏模式不需要激活它也能用。
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "刷吧!少年已获得设备管理权限", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "刷吧!少年设备管理权限已关闭", Toast.LENGTH_SHORT).show()
    }
}
