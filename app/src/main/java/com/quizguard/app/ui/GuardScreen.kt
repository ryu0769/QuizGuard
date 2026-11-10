package com.quizguard.app.ui

import android.content.Intent
import android.provider.Settings
import com.quizguard.app.BuildConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quizguard.app.R
import com.quizguard.app.core.CatalogOption
import com.quizguard.app.core.DeviceCompat
import com.quizguard.app.core.Feedback
import com.quizguard.app.core.GuardControl
import com.quizguard.app.core.GuardState
import com.quizguard.app.core.ParentPrefs
import com.quizguard.app.core.Phase
import com.quizguard.app.core.Question
import com.quizguard.app.core.QuestionBank
import com.quizguard.app.core.Stage
import com.quizguard.app.core.Term
import com.quizguard.app.core.WrongPolicy
import com.quizguard.app.core.WrongBook
import com.quizguard.app.core.WrongEntry

@Composable
fun GuardScreen(onExitApp: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            !GuardState.running -> OffScreen()
            GuardState.parentMode -> ParentPanel(onExitApp = onExitApp)
            GuardState.phase == Phase.QUIZ -> QuizScreen()
            else -> IdleScreen()
        }
    }
}

/** v1.0.9 品牌头像：夜读少年插画，圆形裁切 + 主色描边 */
@Composable
private fun AppAvatar(size: Dp) {
    Image(
        painter = painterResource(R.drawable.app_avatar_night_study),
        contentDescription = "刷吧!少年头像",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
    )
}

/** 守护未开启：开关页（可随时开启） */
@Composable
private fun OffScreen() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            AppAvatar(96.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "刷吧!少年",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "shuaba.app · 儿童手机时长管家",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "开启后将在后台守护：可正常使用、切换应用；每 ${GuardState.cycleMs / 60000} 分钟自动弹出 ${GuardState.quizCount} 道题，全部答对才能继续玩。答对自动跳下一题，答错会停下来讲解，看懂后手动继续。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "出题范围：${QuestionBank.describe(GuardState.selection)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { GuardControl.start(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("开启守护", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "默认家长密码 1234，请尽快在家长控制台修改。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "刷吧!少年 v${BuildConfig.VERSION_NAME} · shuaba.app",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 守护中：倒计时 + 提示，可自由切换后台；右下角家长入口，底部可停止 */
@Composable
private fun IdleScreen() {
    val context = LocalContext.current
    var showPwd by remember { mutableStateOf(false) }
    var showStopPwd by remember { mutableStateOf(false) }
    var showBook by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "刷吧!少年正在守护",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "下次答题倒计时",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = GuardState.formatRemaining(),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "可正常使用手机、切换后台。时间到会出现 ${GuardState.quizCount} 道题，全部答对才能继续玩；答错会停下来讲解，看懂后手动继续。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "出题范围：${QuestionBank.describe(GuardState.selection)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            // 孩子也能查看自己的错题（只读：不能移除 / 清空），无需家长密码
            @Suppress("UNUSED_VARIABLE")
            val bookVersionIdle = WrongBook.version
            TextButton(onClick = { showBook = true }) {
                Text(
                    "📕 我的错题本（${WrongBook.stats().total} 题）",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            GuardState.notice?.let {
                Spacer(Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 15.sp
                    )
                }
            }
        }

        // 右下角低调的家长入口
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Button(
                onClick = { showPwd = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "家长",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        // 左下角：停止守护（需家长密码，避免儿童自行关闭）
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            TextButton(onClick = { showStopPwd = true }) {
                Text(
                    "停止守护",
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showPwd) {
        PasswordDialog(
            onDismiss = { showPwd = false },
            onSuccess = {
                showPwd = false
                GuardState.enterParentMode()
            }
        )
    }

    if (showStopPwd) {
        PasswordDialog(
            title = "家长验证（停止守护）",
            onDismiss = { showStopPwd = false },
            onSuccess = {
                showStopPwd = false
                GuardControl.stop(context)
            }
        )
    }

    if (showBook) {
        WrongBookDialog(onDismiss = { showBook = false }, readOnly = true)
    }
}

/** 家长密码输入框 */
@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    title: String = "家长验证"
) {
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("请输入家长密码（默认 1234）", fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pwd,
                    onValueChange = {
                        pwd = it.filter { c -> c.isDigit() }.take(8)
                        error = null
                    },
                    label = { Text("密码") },
                    singleLine = true,
                    isError = error != null
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = Color(0xFFB71C1C), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (ParentPrefs.verify(pwd)) {
                    onSuccess()
                } else {
                    error = when {
                        ParentPrefs.isLocked() -> "已锁定，请 ${ParentPrefs.lockSecondsLeft()} 秒后再试"
                        else -> "密码错误，还可尝试 ${ParentPrefs.attemptsLeft()} 次"
                    }
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 家长控制台：验证通过后展示（可滚动） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentPanel(onExitApp: () -> Unit) {
    val context = LocalContext.current
    var showChange by remember { mutableStateOf(false) }
    var showWrongBook by remember { mutableStateOf(false) }

    // 题库在后台线程解析，完成后 version 变化 → 本面板重组，题数统计自动刷新
    @Suppress("UNUSED_VARIABLE")
    val bankRefresh = QuestionBank.version

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            AppAvatar(64.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                "家长控制台",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "默认密码 1234，建议尽快修改。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // ① 答题间隔
            SettingTitle("每轮答题间隔（当前 ${GuardState.cycleMs / 60000} 分钟）")
            ChipRow(
                items = listOf(5, 10, 15, 20, 30),
                labelOf = { "${it}分" },
                selectedOf = { (GuardState.cycleMs / 60000) == it.toLong() },
                onClick = { GuardState.setCycle(it) }
            )
            Spacer(Modifier.height(18.dp))

            // ② 出题范围（v1.0.4 四级联动：学段 → 年级 → 册别 → 科目）
            SettingTitle("出题范围（当前：${QuestionBank.describe(GuardState.selection)}）")
            CatalogPicker()
            Spacer(Modifier.height(18.dp))

            // ③ 每次做题数量（v1.0.3 新增）
            SettingTitle("每次做题数量（当前 ${GuardState.quizCount} 道）")
            ChipRow(
                items = listOf(1, 2, 3, 4, 5),
                labelOf = { "${it}道" },
                selectedOf = { it == GuardState.quizCount },
                onClick = { GuardState.changeQuizCount(it) }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "修改后下一轮答题生效；当前这组题不会被打断。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            // ④ 答错处理（v1.0.5 新增）
            SettingTitle("答错处理（当前：${GuardState.wrongPolicy.label}）")
            ChipRow(
                items = WrongPolicy.entries.toList(),
                labelOf = { it.label },
                selectedOf = { it == GuardState.wrongPolicy },
                onClick = { GuardState.changeWrongPolicy(it) }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                GuardState.wrongPolicy.hint + "。无论哪种，答错都会停在解析页，" +
                    "孩子看懂后手动点「下一题」才继续。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))

            // ⑤ 错题本 + 艾宾浩斯遗忘曲线（v1.0.6 新增）
            @Suppress("UNUSED_VARIABLE")
            val bookVersion = WrongBook.version
            val bookStats = WrongBook.stats()
            SettingTitle("📕 错题本（共 ${bookStats.total} 题 · 待复习 ${bookStats.due} · 已掌握 ${bookStats.mastered}）")
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "艾宾浩斯复习节点：" + WrongBook.INTERVAL_LABELS.joinToString(" → "),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "答错即入本；到期错题优先混入每轮答题，答对进入下一档，答错回到 5 分钟后重刷；" +
                            "连续 ${WrongBook.INTERVALS.size} 档全对即标记「已掌握」，不再出现。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bookStats.nextDueAt?.let {
                            "最近一次复习：约 " +
                                WrongBook.formatDelta(it - System.currentTimeMillis()) + "后"
                        } ?: "暂无待复习错题",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showWrongBook = true },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("查看 / 管理错题", fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(18.dp))

            // ⑥ 每轮错题复习数量 + 额外加练开关
            SettingTitle("每轮错题复习（当前 ${GuardState.reviewPerRound} 道）")
            ChipRow(
                items = listOf(0, 1, 2, 3),
                labelOf = { if (it == 0) "关闭" else "${it}道" },
                selectedOf = { it == GuardState.reviewPerRound },
                onClick = { GuardState.changeReviewPerRound(it) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "额外加练（不占本轮题数）",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "开启后本轮题数 = 新题 ${GuardState.quizCount} 道 + 到期错题",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = GuardState.reviewExtra,
                    onCheckedChange = { GuardState.changeReviewExtra(it) }
                )
            }
            Spacer(Modifier.height(18.dp))

            // 锁定状态卡片：如实告知系统钉屏是否生效，并给出开启入口
            Surface(
                color = if (GuardState.lockActive)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (GuardState.lockActive) "🔒 系统锁定已生效"
                        else "⚠️ 系统锁定未生效：答题时可切换其它程序",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (GuardState.lockActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        GuardState.lockHint ?: if (GuardState.lockActive)
                            "Home / 多任务键已被系统禁用，答题期间无法切换程序。"
                        else
                            "当前不在答题阶段，尚未检测系统锁定状态。" +
                                "答题时切走（含最近任务）0.5 秒内自动拉回；" +
                                "从任务栏清除进程会立即恢复，进程真被系统回收时 8 秒内也会复活并重新出题。" +
                                "要彻底隐藏最近任务键，请开启系统屏幕固定或设为设备所有者。",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = if (GuardState.lockActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
                    )
                    if (!GuardState.lockActive) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(
                                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text("去开启系统「屏幕固定」", fontSize = 14.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "三步具体操作见下方「🔒 锁死向导」；彻底锁死还可把本 App 设为设备所有者。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // v1.0.16：锁死向导（屏幕固定三步设置，解决「手速快切后台杀进程」）
            LockGuideCard()
            Spacer(Modifier.height(12.dp))

            // v1.0.14：机型适配检查（逐项 ✓/✗/○ + 一键跳转系统设置）
            CompatCard()
            Spacer(Modifier.height(12.dp))

            Text(
                "v1.0.16：修复「第 2 轮起不再申请系统锁定」——现在每轮答题都会重新锁定，" +
                    "屏幕固定正确开启后 Home 与最近任务整个消失，孩子没法切出去杀进程。" +
                    "新增「🔒 锁死向导」检测屏幕固定 / 取消需解锁 / 设备所有者三项状态。" +
                    "兜底仍是：切走 0.35 秒拉回、上滑清任务秒恢复、进程真被回收 8 秒内复活重出题。" +
                    "错题本按艾宾浩斯曲线（5分钟 → 30分钟 → 12小时 → 1/2/4/7/15/30天）重刷。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { GuardState.exitParentMode() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("继续守护（${GuardState.cycleMs / 60000} 分钟）", fontSize = 17.sp) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showChange = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) { Text("修改密码", fontSize = 17.sp) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { GuardState.testFire() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) { Text("立即弹出测试题", fontSize = 17.sp) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) { Text("退出管控", fontSize = 17.sp) }
            Spacer(Modifier.height(12.dp))
            Text(
                "退出后若已设为设备所有者，需通过电脑命令解除。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "刷吧!少年 v${BuildConfig.VERSION_NAME} · shuaba.app",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showChange) {
        ChangePasswordDialog(onDismiss = { showChange = false })
    }

    if (showWrongBook) {
        WrongBookDialog(onDismiss = { showWrongBook = false })
    }
}

/**
 * v1.0.16 锁死向导：三步让「答题时切后台杀进程」失效。
 *
 * 背景：普通安装下，唯一能真正阻止「切最近任务 → 上滑清进程」的手段是系统「屏幕固定」。
 * 但该开关默认关闭，且「取消固定」默认不需要密码——孩子能自己取消，锁定等于没做。
 * 本卡片把三步做成可检测清单：屏幕固定开关 / 取消需解锁 / 设备所有者，
 * 家长照着开一次，之后每轮答题都会自动锁定。
 */
@Composable
private fun LockGuideCard() {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            tick++
        }
    }
    val pinning = remember(tick) { DeviceCompat.screenPinningEnabled(context) }
    val exitLocked = remember(tick) { DeviceCompat.screenPinningExitLocked(context) }
    val owner = remember(tick) { DeviceCompat.isDeviceOwner(context) }
    val lockedNow = remember(tick) { DeviceCompat.isLockTaskActive(context) }

    // Device Owner 最彻底；普通模式需「固定开启 + 取消需解锁」两步都到位才算就绪
    val ready = owner || (pinning == true && exitLocked == true)
    val fg = if (ready) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onErrorContainer
    val container = if (ready) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.errorContainer

    Surface(color = container, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (ready) "🔒 锁死已就绪：答题时切不出去"
                else "🔒 锁死向导：3 步让「切后台杀进程」失效",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                DeviceCompat.screenPinningHint(),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = fg
            )
            Spacer(Modifier.height(8.dp))

            GuideLine("①", "系统「屏幕固定」", pinning, fg)
            GuideLine("②", "取消固定需要解锁", exitLocked, fg)
            GuideLine("③", "设备所有者（可选，最彻底）", owner, fg)

            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    owner -> "已设为设备所有者：答题锁定无弹窗，孩子也无法取消固定。"
                    pinning == true && exitLocked == true ->
                        "设置已到位：每轮答题开始都会自动锁定，Home 与最近任务被系统隐藏。"
                    pinning == false ->
                        "屏幕固定未开启——这正是孩子能切出去杀进程的根本原因。"
                    else -> "开启后请顺手关掉「固定前询问」，否则每轮答题会多一次系统确认框。"
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = fg
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "本次状态：" + if (lockedNow) "已锁定（系统锁定生效中）" else "未锁定（不在答题阶段或未生效）",
                fontSize = 11.sp,
                color = fg
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { DeviceCompat.openSecuritySettings(context) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("打开安全设置", fontSize = 14.sp) }
            Spacer(Modifier.height(6.dp))
            Text(
                "最强模式（电脑执行一次，之后孩子无法退出）：\n" +
                    "adb shell dpm set-device-owner com.quizguard.app/.AdminReceiver",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = fg
            )
        }
    }
}

/** 向导中的一行：① / ② / ③ + 名称 + 三态 */
@Composable
private fun GuideLine(no: String, title: String, value: Boolean?, fg: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(no, fontSize = 12.sp, color = fg, modifier = Modifier.width(20.dp))
        Text(title, fontSize = 13.sp, color = fg, modifier = Modifier.weight(1f))
        Text(
            when (value) {
                true -> "✓ 已开启"
                false -> "✗ 未开启"
                null -> "○ 请确认"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when (value) {
                true -> Color(0xFF1B5E20)
                false -> Color(0xFFB71C1C)
                null -> fg
            }
        )
    }
}

/**
 * v1.0.14 手机适配卡片：逐项显示 ✓ / ✗ / ○，点击直接跳到对应的系统设置页。
 * 各 ROM 没有统一的自启动查询 API，所以该项目固定显示「○ 请确认」，由家长按说明手工勾选。
 * 从系统设置返回不会触发重组，这里用 1.5 秒轮询刷新检测结果（仅在家长台可见时运行）。
 */
@Composable
private fun CompatCard() {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            tick++
        }
    }
    val items = remember(tick) { DeviceCompat.checklist(context) }
    val missing = items.count { it.status == DeviceCompat.Status.MISSING }
    val allOk = missing == 0
    val fg = if (allOk) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        color = if (allOk) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (allOk) "📱 手机适配：关键权限已就绪"
                else "📱 手机适配：还有 $missing 项未开启",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = fg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${DeviceCompat.romLabel()} · 第三方 App 无法绕过系统限制，以下需手动确认",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = fg
            )
            Spacer(Modifier.height(8.dp))

            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (item.status) {
                            DeviceCompat.Status.OK -> "✓"
                            DeviceCompat.Status.MISSING -> "✗"
                            DeviceCompat.Status.UNKNOWN -> "○"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            DeviceCompat.Status.OK -> Color(0xFF1B5E20)
                            DeviceCompat.Status.MISSING -> Color(0xFFB71C1C)
                            DeviceCompat.Status.UNKNOWN -> MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.width(22.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                        Text(
                            item.detail,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = fg
                        )
                    }
                    TextButton(onClick = { item.onOpen(context) }) {
                        Text(item.action, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "○ = 无法自动检测，请按说明到系统设置确认一次；改完返回本页会自动刷新。",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = fg
            )
        }
    }
}

/** 设置项小标题 */
@Composable
private fun SettingTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * 出题范围四级联动选择器（v1.0.4）：
 * 学段（小学/初中/高中）→ 年级 → 册别（上册/下册/全部）→ 科目（语数英/其它学科/全部）。
 * 每一级只列出 Catalog 中真实存在的选项；无题库的档位置灰但保留可见，作为接入位提示。
 */
@Composable
private fun CatalogPicker() {
    // 后台解析完成后 version 变化 → 本组件重组，题数统计刷新
    @Suppress("UNUSED_VARIABLE")
    val refresh = QuestionBank.version
    val sel = GuardState.selection

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            PickerLine("学段", QuestionBank.stages(), sel.stage.id) {
                GuardState.setStage(Stage.from(it.id))
            }
            PickerLine("年级", QuestionBank.grades(sel.stage), sel.gradeId) {
                GuardState.setGrade(it.id)
            }
            PickerLine("册别", QuestionBank.terms(sel.stage, sel.gradeId), sel.term.id) {
                GuardState.setTerm(Term.from(it.id))
            }
            PickerLine("科目", QuestionBank.subjects(sel.stage, sel.gradeId, sel.term), sel.subjectId) {
                GuardState.setSubject(it.id)
            }

            Text(
                QuestionBank.status(sel),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "年级选「综合（跨年级）」= 该学段全部年级题库合并，数学等各科都能跨年级混合出题；" +
                    "科目选「全部」= 每个科目先各抽 1 道再随机补足；" +
                    "册别选「全部」= 上册 + 下册 + 全册题库合并。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "置灰项表示暂无题库。接入初中 / 高中多科目：往 assets/banks/junior、senior " +
                    "按 QUESTION_BANK_FORMAT.md 丢 JSON 即自动出现，无需改代码。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单行选择器：小标题 + 选项 chips */
@Composable
private fun PickerLine(
    title: String,
    options: List<CatalogOption>,
    selectedId: String,
    onPick: (CatalogOption) -> Unit
) {
    Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    Spacer(Modifier.height(4.dp))
    OptionRow(options, selectedId, onPick)
    Spacer(Modifier.height(10.dp))
}

/** 选项 chips：选中高亮；无题库项置灰且不可点 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(
    options: List<CatalogOption>,
    selectedId: String,
    onPick: (CatalogOption) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            val selected = opt.id == selectedId
            AssistChip(
                onClick = { if (opt.enabled) onPick(opt) },
                enabled = opt.enabled,
                label = { Text(opt.label, fontSize = 13.sp) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    labelColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/** 通用选项条：题数 / 间隔共用，选中项高亮 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    items: List<T>,
    labelOf: @Composable (T) -> String,
    selectedOf: (T) -> Boolean,
    onClick: (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val selected = selectedOf(item)
            AssistChip(
                onClick = { onClick(item) },
                label = { Text(labelOf(item)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * 错题本详情（v1.0.6）：家长可逐题查看复习档位与下次复习时间，移除单题或清空整本。
 */
@Composable
private fun WrongBookDialog(onDismiss: () -> Unit, readOnly: Boolean = false) {
    @Suppress("UNUSED_VARIABLE")
    val refresh = WrongBook.version
    val all = WrongBook.list()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空错题本？") },
            text = {
                Text(
                    "将删除全部 ${all.size} 道错题及其复习进度，此操作不可恢复。",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { WrongBook.clear(); confirmClear = false; onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (readOnly) "📕 我的错题本（${all.size} 题）" else "📕 错题本（${all.size} 题）")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (all.isEmpty()) {
                    Text(
                        "还没有错题。孩子答错一道题后会自动收进来，并按遗忘曲线反复复习。",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    Text(
                        "复习节点：" + WrongBook.INTERVAL_LABELS.joinToString(" → "),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    all.take(50).forEach { e -> WrongEntryRow(e, readOnly) }
                    if (all.size > 50) {
                        Spacer(Modifier.height(8.dp))
                        Text("仅显示前 50 题，共 ${all.size} 题。", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            if (!readOnly && all.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("清空错题本", color = Color(0xFFB71C1C))
                }
            }
        }
    )
}

/** 错题本单条：题干 + 错误次数 + 当前档位 + 下次复习时间 + 移除 */
@Composable
private fun WrongEntryRow(e: WrongEntry, readOnly: Boolean = false) {
    Surface(
        color = if (e.mastered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                e.question.text.take(46).let {
                    if (e.question.text.length > 46) "$it…" else it
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${e.question.subjectLabel} · 错 ${e.wrongCount} 次 · 复习答对 ${e.reviewCount} 次",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (e.mastered) "🎉 已掌握"
                    else "第 ${e.stage + 1}/${WrongBook.INTERVALS.size} 档 · 下次 " +
                        WrongBook.formatDelta(e.dueAt - System.currentTimeMillis()) + "后",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!readOnly) {
                    TextButton(
                        onClick = { WrongBook.remove(e.key) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text("移除", fontSize = 11.sp) }
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** 修改密码对话框 */
@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column {
                OutlinedTextField(
                    value = oldPwd,
                    onValueChange = { oldPwd = it.filter { c -> c.isDigit() }.take(8); error = null },
                    label = { Text("原密码") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it.filter { c -> c.isDigit() }.take(8); error = null },
                    label = { Text("新密码（≥4 位）") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(8); error = null },
                    label = { Text("确认新密码") },
                    singleLine = true
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = Color(0xFFB71C1C), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    newPwd.length < 4 -> error = "新密码至少 4 位"
                    newPwd != confirm -> error = "两次输入不一致"
                    !ParentPrefs.change(oldPwd, newPwd) -> error = "原密码错误"
                    else -> onDismiss()
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 答题中（v1.0.5）：点选项即作答，不再需要「提交答案」按钮。
 * - 答对 → 短暂绿色反馈后【自动跳过】到下一题，最后一道直接放行；
 * - 答错 → 停在原题，展示正确答案 + 解题思路，必须手动点「看懂了，下一题」才继续。
 * 内容区可滚动，保证解析再长也不会把底部按钮挤出屏幕。
 * 全屏遮挡 + 钉屏 / 防逃逸，必须把本组题（含错题重做）全部答对才放行。
 */
@Composable
private fun QuizScreen() {
    var showPwd by remember { mutableStateOf(false) }
    val q: Question? = GuardState.questions.getOrNull(GuardState.current)

    // 兜底：题目为空时直接给家长逃生口，避免「锁屏 + 空白界面」死锁
    if (GuardState.questions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("题目暂时未加载", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(12.dp))
                Text("点击家长解锁后重试或退出", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { showPwd = true }) { Text("家长解锁") }
            }
        }
        if (showPwd) {
            PasswordDialog(onDismiss = { showPwd = false }, onSuccess = { showPwd = false; GuardState.enterParentMode() })
        }
        return
    }

    val question = q ?: return
    val feedback = GuardState.feedback
    val correctIndex = question.answerIndex
    val isLast = GuardState.current + 1 >= GuardState.questions.size

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 进度 + 科目 + 家长入口
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(question.subjectLabel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    // v1.0.6：来自错题本的复习题，标出当前是第几次复习
                    if (question.reviewKey != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("📕 复习 ${question.reviewStage + 1}", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showPwd = true }) {
                        Text("家长", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(
                        text = "第 ${GuardState.current + 1} / ${GuardState.questions.size} 题",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 「错题重做轮」提示
            GuardState.roundLabel?.let {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔁 $it",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // 锁定状态提示条：进程内只在【第一次进入答题】显示，关闭进程重开才再显示（v1.0.3）
            if (GuardState.showIntro) {
                Surface(
                    color = if (GuardState.lockActive)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (GuardState.lockActive)
                            "🔒 已锁定：Home / 多任务键已被禁用"
                        else
                            "⚠️ 系统屏幕固定未开启：切走（含最近任务）0.5 秒内自动拉回，上滑清任务会立即恢复；答完 ${GuardState.questions.size} 题才能继续使用",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = if (GuardState.lockActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 可滚动内容区：题目 + 选项 + 解析，底部按钮始终固定可见
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = question.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 34.sp
                )

                Spacer(Modifier.height(24.dp))

                question.options.forEachIndexed { index, option ->
                    val isSelected = GuardState.selected == index
                    val isAnswer = index == correctIndex
                    // 判定后把正确项标绿，方便对照理解
                    val showRight = feedback != Feedback.NONE && isAnswer
                    val bg = when {
                        showRight -> Color(0xFFC8E6C9)
                        feedback == Feedback.WRONG && isSelected -> Color(0xFFFFCDD2)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val txtColor = when {
                        showRight -> Color(0xFF1B5E20)
                        feedback == Feedback.WRONG && isSelected -> Color(0xFFB71C1C)
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            // v1.0.5：点选项即判定；判定后禁止再点，避免误触跳过解析
                            .clickable(enabled = feedback == Feedback.NONE) { GuardState.answer(index) },
                        color = bg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ABCD".getOrElse(index) { '?' }.toString(),
                                fontWeight = FontWeight.Bold,
                                color = txtColor,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(text = option, fontSize = 18.sp, color = txtColor)
                            if (showRight) {
                                Spacer(Modifier.weight(1f))
                                Text("✓ 正确答案", fontSize = 13.sp, color = Color(0xFF1B5E20))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                when (feedback) {
                    Feedback.CORRECT -> {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isLast && GuardState.roundLabel == null)
                                    "✅ 答对啦！本组全部完成，马上继续玩…"
                                else "✅ 答对啦！马上进入下一题…",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Feedback.WRONG -> {
                        // 关键：答错后没有任何自动跳转，孩子可以慢慢看解析
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    text = "❌ 答错了 · 正确答案：" +
                                        "ABCD".getOrElse(correctIndex) { '?' } + ". " +
                                        question.options.getOrElse(correctIndex) { "" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    lineHeight = 22.sp
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "解题思路",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = question.explanation.takeIf { it.isNotBlank() }
                                        ?: "本题暂无解析，请对照正确答案再想一想。",
                                    fontSize = 15.sp,
                                    lineHeight = 23.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "慢慢看，看懂后点下方按钮继续；做错的题稍后还要再答对一次才能放行。",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Feedback.NONE -> {
                        Text(
                            text = "👆 点一下选项就作答：答对自动进入下一题，答错会停下来讲解。",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // v1.0.6：复习题判定后显示艾宾浩斯下一次复习节点
                if (feedback != Feedback.NONE) {
                    question.reviewKey?.let { key ->
                        WrongBook.nextHint(key)?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // 底部操作区：答错必须【手动】点，答对由状态机自动跳，无需操作
            when (feedback) {
                Feedback.WRONG -> {
                    val label = when {
                        GuardState.wrongPolicy == WrongPolicy.RESTART_GROUP -> "看懂了，重新作答本组"
                        isLast -> "看懂了，去改错"
                        else -> "看懂了，下一题"
                    }
                    Button(
                        onClick = { GuardState.continueAfterWrong() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }

                Feedback.CORRECT -> {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isLast) "✅ 本组完成，正在放行…" else "✅ 正在自动进入下一题…",
                                fontSize = 16.sp,
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }

                Feedback.NONE -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "请选择一个答案",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPwd) {
        PasswordDialog(
            onDismiss = { showPwd = false },
            onSuccess = { showPwd = false; GuardState.enterParentMode() }
        )
    }
}
