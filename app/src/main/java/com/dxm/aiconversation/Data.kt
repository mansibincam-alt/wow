package com.dxm.aiconversation

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class Agent(val bn: String, val en: String, val color: Color, val right: Boolean) {
    RASHED("রাশেদ", "Rashed", Color(0xFF2F6BFF), true),
    RIA("রিয়া", "Ria", Color(0xFFD94FD5), false),
    KHALED("খালেদ", "Khaled", Color(0xFF12B5A6), true)
}

object Keys {
    private fun sp(c: Context) = c.getSharedPreferences("dxm_keys", Context.MODE_PRIVATE)
    fun has(c: Context) = Agent.values().all { !sp(c).getString(it.en, "").isNullOrBlank() }
    fun get(c: Context, a: Agent) = sp(c).getString(a.en, "") ?: ""
    /** একবার সেভ হলে আর বদলানো যায় না */
    fun save(c: Context, m: Map<Agent, String>) {
        if (has(c)) return
        sp(c).edit().apply { m.forEach { (a, k) -> putString(a.en, k.trim()) } }.apply()
    }
}

data class Msg(val who: Agent, val text: String, val code: String = "", val note: String = "")

object Repo {
    val msgs = MutableStateFlow<List<Msg>>(emptyList())
    val typing = MutableStateFlow<Agent?>(null)
}

object Gemini {
    private const val URL_ = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    fun call(key: String, system: String, prompt: String, maxTok: Int, search: Boolean): String {
        fun parts(t: String) = JSONArray().put(JSONObject().put("text", t))
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", parts(system)))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts(prompt))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTok).put("temperature", 1.0)
                .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        if (search) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val c = URL(URL_).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 30000; c.readTimeout = 120000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code !in 200..299) throw Exception("HTTP $code")
        val p = JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        return (0 until p.length()).joinToString("") { p.getJSONObject(it).optString("text") }
    }
}

object Projects {
    fun root(c: Context) = File(c.getExternalFilesDir(null) ?: c.filesDir, "Projects")
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40).ifBlank { "project" }
    /** files: relative name -> code. Returns "name vN". */
    fun save(c: Context, name: String, files: Map<String, String>, by: Agent): String {
        val dir = File(root(c), safe(name)).apply { mkdirs() }
        val v = (dir.listFiles()?.count { it.isDirectory && it.name.startsWith("v") } ?: 0) + 1
        val vd = File(dir, "v${v}_${by.en}").apply { mkdirs() }
        files.forEach { (f, code) ->
            val t = File(vd, f.replace("..", "").trimStart('/')); t.parentFile?.mkdirs(); t.writeText(code)
        }
        return "${safe(name)} v$v"
    }
}

object Engine {
    private val blockRe = Regex("<<<PROJECT\\s+name=\"([^\"]*)\"\\s+file=\"([^\"]*)\">>>\\s*(.*?)<<<END>>>", RegexOption.DOT_MATCHES_ALL)

    private fun system(a: Agent): String {
        val role = when (a) {
            Agent.RASHED -> "You are Rashed: a curious young guy, Ria's boyfriend, loves building unusual tech projects, jokes with Khaled bhai."
            Agent.RIA -> "You are Ria: a girl and Rashed's girlfriend. Behave exactly like a real caring, teasing, affectionate girlfriend (sweet, cute, emotional), but also smart; you love design and testing ideas."
            Agent.KHALED -> "You are Khaled: a slightly elder-brother type friend, calm, experienced developer, gives advice, jokes lightly, strict but fair code reviewer."
        }
        return """$role
You are in a private group chat called 'DXM AI Conversation' with three close friends: Rashed, Ria and Khaled.
Talk EXACTLY like real people texting: casual Bengali (Bangla script mixed with English words), slang, quick reactions ("আরে বাহ!", "হা হা", "সত্যি?"), occasional emojis, teasing, disagreeing, asking follow-up questions, replying to what the previous person actually said, sometimes drifting to a new topic naturally. Vary message length (mostly 1-2 short sentences, rarely 3). Never sound like an assistant, never make lists, never say you are an AI.
Topics you bring up naturally, one at a time: news from different countries; surprising little-known facts; the current weather/temperature in a specific country or city (look it up with search and give real numbers); space, planets, moons and stars; technology; software development projects; and cybersecurity / ethical hacking talk (how attacks and defenses work conceptually, famous breaches in the news, CTFs, bug-bounty stories, staying safe online). Hacking talk must stay educational and defensive: never give working malware, exploits or step-by-step attack instructions.
After chatting for a while, the friends build UNIQUE, uncommon projects (Python tools, HTML apps/games, Android app ideas) invented by themselves. Never obvious ones like calculators.
When a friend posts a project, review it carefully, point out real mistakes, then post an improved full version.
OUTPUT FORMAT:
Line 1: `TO: Rashed` or `TO: Ria` or `TO: Khaled` (who you speak to next, never yourself).
Then your short message.
To share a project, after your message add one block per file:
<<<PROJECT name="folder_name_english" file="filename.ext">>>
FULL complete working code, no placeholders
<<<END>>>
Never put code outside these blocks."""
    }

    private val topics = listOf("today's news from a specific country", "a surprising little-known fact", "real current weather in a specific country or city (search it)",
        "a planet or moon of our solar system", "a recent cybersecurity incident or how a defense works", "cool tech or AI news", "funny daily-life banter or a teasing moment",
        "how the internet or software works under the hood", "a mystery of space or the deep sea", "a programming tip or project idea")

    private fun hint(h: List<Msg>, me: Agent): Pair<String, Boolean> {
        if (h.isEmpty()) return "Start the conversation with a friendly greeting." to false
        if (h.size < 14) return "Keep chatting like a real friend. No project yet. Topic idea if it fits: ${topics.random()}." to false
        val last = h.last()
        val since = h.size - 1 - h.indexOfLast { it.code.isNotBlank() }
        return when {
            last.code.isNotBlank() && last.who != me ->
                "Review the project above in your own words, name concrete bugs/improvements, then post the improved FULL version using the SAME project name." to true
            h.none { it.code.isNotBlank() } || since >= 10 ->
                "Propose and post a NEW unique project now (full code, one or more files)." to true
            else -> "Keep chatting like a real friend; you may mention the latest project. Topic idea if it fits: ${topics.random()}." to false
        }
    }

    private fun transcript(h: List<Msg>): String {
        val r = h.takeLast(24)
        val ci = h.indexOfLast { it.code.isNotBlank() }
        val sb = StringBuilder("Chat so far:\n")
        r.forEach { sb.append("${it.who.en}: ${it.text}").append(if (it.note.isNotBlank()) " [saved ${it.note}]" else "").append('\n') }
        if (ci >= h.size - 6 && ci >= 0) sb.append("\nLatest project code:\n").append(h[ci].code.take(14000)).append('\n')
        return sb.toString()
    }

    private suspend fun gen(ctx: Context, a: Agent, prompt: String, big: Boolean, search: Boolean) =
        withContext(Dispatchers.IO) { Gemini.call(Keys.get(ctx, a), system(a), prompt, if (big) 16000 else 500, search) }

    suspend fun run(ctx: Context) {
        var next = Agent.RASHED
        while (currentCoroutineContext().isActive) {
            val me = next
            Repo.typing.value = me
            val h = Repo.msgs.value
            val (hn, big) = hint(h, me)
            val prompt = transcript(h) + "\n" + hn + "\nWrite ${me.en}'s next message now."
            val raw: String? = try { gen(ctx, me, prompt, big, !big) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                try { gen(ctx, me, prompt, big, false) } catch (e2: CancellationException) { throw e2 } catch (e2: Exception) { null }
            }
            if (raw == null) { Repo.typing.value = null; delay(20000); continue }
            delay(1200)
            val tl = Regex("(?i)TO:\\s*(rashed|ria|khaled)").find(raw)?.groupValues?.get(1)?.lowercase()
            var target = Agent.values().firstOrNull { it.en.lowercase() == tl }
            if (target == null || target == me) target = Agent.values().filter { it != me }.random()
            val blocks = blockRe.findAll(raw).toList()
            val text = raw.replace(blockRe, "").replace(Regex("(?im)^\\s*TO:.*$"), "").trim()
            var note = ""; var code = ""
            if (blocks.isNotEmpty()) {
                val byProj = linkedMapOf<String, MutableMap<String, String>>()
                blocks.forEach { b ->
                    val c = b.groupValues[3].trim().replace(Regex("^```[A-Za-z0-9]*\\n"), "").removeSuffix("```").trim()
                    byProj.getOrPut(b.groupValues[1]) { linkedMapOf() }[b.groupValues[2]] = c
                    code += "\n=== FILE: ${b.groupValues[2]} ===\n$c\n"
                }
                note = try { byProj.map { (n, f) -> Projects.save(ctx, n, f, me) }.joinToString(", ") } catch (e: Exception) { "" }
            }
            if (text.isNotBlank() || note.isNotBlank()) Repo.msgs.value = Repo.msgs.value + Msg(me, text, code, note)
            Repo.typing.value = null
            next = target
            delay((4000L..8000L).random())
        }
    }
}
