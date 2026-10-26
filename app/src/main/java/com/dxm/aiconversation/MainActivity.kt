package com.dxm.aiconversation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.security.MessageDigest

const val CREDIT = "Developed by DXM"
private const val PASS_HASH = "bd27d7b3aee8f6ea296f3d29452b3fd181deb9326209fc4fd843bb69aa2aa389"
private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF4C8DFF), surface = Color(0xFF0E1630))) {
                var screen by remember { mutableStateOf(if (Repo.msgs.value.isEmpty()) 0 else 2) }
                when (screen) {
                    0 -> Splash { screen = 1 }
                    1 -> StartScreen { screen = 2 }
                    else -> ChatScreen()
                }
            }
        }
    }
}

@Composable
fun Bg(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040612), Color(0xFF0A1A44), Color(0xFF111B3A))))
            .drawBehind {
                val c = Offset(size.width * .85f, size.height * .12f)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .10f), Color.Transparent), center = c, radius = size.width * .7f), radius = size.width * .7f, center = c)
            }, content = content
    )
}

@Composable
fun Logo(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val cr = CornerRadius(s * .28f)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF1E4FD8), Color(0xFF05060F)), start = Offset(0f, 0f), end = Offset(s, s)), cornerRadius = cr)
        drawRoundRect(Color.White.copy(alpha = .25f), cornerRadius = cr, style = Stroke(s * .015f))
        val r = s * .17f
        listOf(Agent.RASHED to Offset(s * .36f, s * .40f), Agent.KHALED to Offset(s * .64f, s * .40f), Agent.RIA to Offset(s * .5f, s * .64f)).forEach { (a, o) ->
            drawCircle(a.color.copy(alpha = .9f), r, o)
            drawCircle(Color.White.copy(alpha = .85f), r, o, style = Stroke(s * .018f))
        }
    }
}

@Composable
fun Splash(done: () -> Unit) {
    val sc = remember { Animatable(0.3f) }
    val al = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sc.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 120f)) }
    LaunchedEffect(Unit) { al.animateTo(1f, tween(1000)) }
    LaunchedEffect(Unit) { delay(2800); done() }
    Bg {
        Column(Modifier.align(Alignment.Center).alpha(al.value), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { scaleX = sc.value; scaleY = sc.value }) { Logo(130.dp) }
            Spacer(Modifier.height(24.dp))
            Text("DXM", fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 10.sp, color = Color.White)
            Text("AI Conversation", fontSize = 16.sp, letterSpacing = 4.sp, color = Color.White.copy(alpha = .7f))
        }
        Text(CREDIT, Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).alpha(al.value), color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
    }
}

@Composable
fun StartScreen(onStart: () -> Unit) {
    val ctx = LocalContext.current
    var saved by remember { mutableStateOf(Keys.has(ctx)) }
    val ks = remember { mutableStateMapOf<Agent, String>() }
    var err by remember { mutableStateOf(false) }
    fun proceed() {
        ctx.startForegroundService(Intent(ctx, ChatService::class.java))
        try {
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName))
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${ctx.packageName}")))
        } catch (_: Exception) {}
        onStart()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { proceed() }
    Bg {
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Logo(100.dp)
            Spacer(Modifier.height(20.dp))
            Text("DXM AI Conversation", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("রাশেদ, রিয়া আর খালেদের AI আড্ডা দেখো", color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center)
            if (!saved) {
                Spacer(Modifier.height(24.dp))
                Agent.values().forEach { a ->
                    OutlinedTextField(
                        value = ks[a] ?: "", onValueChange = { ks[a] = it; err = false },
                        label = { Text("${a.bn} এর API Key") }, singleLine = true,
                        isError = err && ks[a].isNullOrBlank(), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                Text("⚠️ একবার সেভ করলে এই key গুলো আর কখনো বদলানো যাবে না।", color = Color(0xFFFFC857), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    val ok = saved || Agent.values().none { ks[it].isNullOrBlank() }
                    if (!ok) err = true else {
                        if (!saved) { Keys.save(ctx, ks.toMap()); saved = true }
                        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else proceed()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(30.dp)
            ) { Text(if (saved) "স্টার্ট" else "সেভ করে স্টার্ট", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
            Text(CREDIT, color = Color.White.copy(alpha = .5f), fontSize = 12.sp)
        }
    }
}

@Composable
fun Avatar(a: Agent, size: Dp) {
    val shape = when (a) { Agent.RASHED -> CircleShape; Agent.RIA -> RoundedCornerShape(percent = 40); Agent.KHALED -> CutCornerShape(percent = 28) }
    Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(a.color, Color(0xFF05060F)))), contentAlignment = Alignment.Center) {
        Text(a.bn.first().toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * .45f).sp)
    }
}

@Composable
fun Bubble(m: Msg) {
    val a = m.who; val r = a.right
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (r) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom
    ) {
        if (!r) { Avatar(a, 34.dp); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.widthIn(max = 290.dp), horizontalAlignment = if (r) Alignment.End else Alignment.Start) {
            Text(a.bn, color = a.color.copy(alpha = 1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            if (m.text.isNotBlank()) Box(
                Modifier.clip(RoundedCornerShape(20.dp, 20.dp, if (r) 20.dp else 4.dp, if (r) 4.dp else 20.dp))
                    .background(Brush.linearGradient(listOf(a.color, a.color.copy(alpha = .6f)))).padding(14.dp, 10.dp)
            ) { Text(m.text, color = Color.White, fontSize = 15.sp) }
            if (m.note.isNotBlank()) Box(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .12f)).padding(10.dp, 6.dp)
            ) { Text("📁 প্রজেক্ট সেভ: ${m.note}", color = Color.White.copy(alpha = .9f), fontSize = 12.sp) }
        }
        if (r) { Spacer(Modifier.width(8.dp)); Avatar(a, 34.dp) }
    }
}

@Composable
fun ChatScreen() {
    val ctx = LocalContext.current as ComponentActivity
    val msgs by Repo.msgs.collectAsState()
    val typing by Repo.typing.collectAsState()
    val ls = rememberLazyListState()
    var dlg by remember { mutableIntStateOf(0) }
    var pass by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) ls.animateScrollToItem(msgs.size - 1) }
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")

    Bg {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(Color.White.copy(alpha = .08f)).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Logo(40.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("DXM AI Conversation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("🟢 ৩ জন অনলাইন", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                }
                Agent.values().forEach { Avatar(it, 28.dp); Spacer(Modifier.width(4.dp)) }
                IconButton(onClick = { dlg = 1 }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = ls, contentPadding = PaddingValues(12.dp)) {
                items(msgs) { Bubble(it) }
                item {
                    typing?.let { Text("${it.bn} টাইপ করছে…", color = it.color, fontSize = 13.sp, modifier = Modifier.padding(8.dp).alpha(pulse)) }
                }
            }
            Text(CREDIT, Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), textAlign = TextAlign.Center, color = Color.White.copy(alpha = .4f), fontSize = 11.sp)
        }
    }
    if (dlg == 1) AlertDialog(
        onDismissRequest = { dlg = 0 }, title = { Text("সেটিংস") },
        text = { Text("DXM AI Conversation v1.0\n$CREDIT") },
        confirmButton = { TextButton(onClick = { dlg = 2; pass = ""; wrong = false }) { Text("অফ") } },
        dismissButton = { TextButton(onClick = { dlg = 0 }) { Text("বন্ধ করো") } }
    )
    if (dlg == 2) AlertDialog(
        onDismissRequest = { dlg = 0 }, title = { Text("পাসওয়ার্ড দাও") },
        text = {
            OutlinedTextField(pass, { pass = it; wrong = false }, singleLine = true, isError = wrong,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                supportingText = { if (wrong) Text("ভুল পাসওয়ার্ড") })
        },
        confirmButton = {
            TextButton(onClick = {
                if (sha(pass) == PASS_HASH) {
                    ctx.stopService(Intent(ctx, ChatService::class.java))
                    ctx.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                } else wrong = true
            }) { Text("নিশ্চিত") }
        },
        dismissButton = { TextButton(onClick = { dlg = 0 }) { Text("বাতিল") } }
    )
}
