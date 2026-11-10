package com.quizguard.app

import android.app.Application
import com.quizguard.app.core.GuardState
import com.quizguard.app.core.ParentPrefs
import com.quizguard.app.core.QuestionBank
import com.quizguard.app.core.WrongBook

/**
 * 应用入口：在进程启动时初始化全局单例，确保 GuardState 在任何组件（Activity / Service）
 * 之前就能拿到 Application Context，从而支持「后台自动弹出答题界面」。
 *
 * 初始化顺序有依赖，勿随意调整：
 *  1. [ParentPrefs.init] —— 先有 SharedPreferences，后续读取家长设置才安全；
 *  2. [WrongBook.init] —— v1.0.6 错题本：从本地 JSON 恢复错题与艾宾浩斯复习档位；
 *  3. [QuestionBank.load] —— 同步递归扫描 assets/banks 建立「学段/年级/册别/科目」Catalog（只读文件头，很快）；
 *  4. [GuardState.attach] —— 注入 Context、恢复家长设置、用 Catalog 修正为有效的题库范围，
 *     并后台预加载该范围的题库（[QuestionBank.prepare] 在 attach 内部调用，不阻塞启动）。
 */
class QuizGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ParentPrefs.init(this)
        WrongBook.init(this)
        QuestionBank.load(this)
        GuardState.attach(this)
    }
}
