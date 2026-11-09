package com.pixeltoonz.app

import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var api: SupabaseClient
    private lateinit var page: LinearLayout
    private var token = ""
    private var refreshToken = ""
    private lateinit var sessionStore: SecureSessionStore
    private var email = ""
    private var name = "Student"
    private var userId = ""
    private var userRole = "student"
    private val purple = 0xFF8055F6.toInt()
    private val backgroundColor = 0xFF12101B.toInt()
    private val surface = 0xFF282338.toInt()
    private val muted = 0xFFB8B0C9.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        sessionStore = SecureSessionStore(this)
        token = sessionStore.accessToken()
        refreshToken = sessionStore.refreshToken()
        email = sessionStore.email()
        name = sessionStore.name()
        if (token.isBlank() && refreshToken.isBlank()) login() else restoreSession()
    }

    private fun screen(title: String, subtitle: String = "") {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), 28.dp)
            setBackgroundColor(backgroundColor)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        setContentView(scroll)
        label(title, 27, Color.WHITE, true)
        if (subtitle.isNotBlank()) label(subtitle, 14, muted)
    }

    private fun label(value: String, size: Int, color: Int = Color.WHITE, bold: Boolean = false) {
        page.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        })
    }

    private fun input(hintText: String, secret: Boolean = false): EditText {
        val v = EditText(this).apply {
            hint = hintText; setTextColor(Color.WHITE); setHintTextColor(muted)
            setPadding(dp(14), 0, dp(14), 0); setBackgroundColor(surface)
            inputType = if (secret) 129 else if (hintText.contains("email", true)) 33 else 1
            layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) }
        }
        page.addView(v); return v
    }

    private fun action(title: String, callback: () -> Unit) {
        page.addView(Button(this).apply {
            text = title; isAllCaps = false; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(purple)
            layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
            setOnClickListener { callback() }
        })
    }

    private fun link(title: String, callback: () -> Unit) {
        page.addView(TextView(this).apply {
            text = title; textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xFFC5ADFF.toInt())
            setPadding(0, dp(15), 0, dp(15)); setOnClickListener { callback() }
        })
    }

    private fun info(title: String, description: String, callback: (() -> Unit)? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(12), dp(15), dp(12))
            setBackgroundColor(surface)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            if (callback != null) setOnClickListener { callback() }
        }
        box.addView(TextView(this).apply { text = title; textSize = 17f; setTextColor(Color.WHITE); setTypeface(null, 1) })
        box.addView(TextView(this).apply { text = description; textSize = 13f; setTextColor(muted); setPadding(0, dp(5), 0, 0) })
        page.addView(box)
    }

    private fun login() {
        screen("PIXELTOONZ ACADEMY", "Your creative learning journey starts here.")
        label("Welcome back", 24, Color.WHITE, true)
        val mail = input("Email address")
        val pass = input("Password", true)
        action("Sign in") {
            val e = mail.text.toString().trim(); val pw = pass.text.toString()
            if (!validEmail(e)) { mail.error = "Enter a valid email"; return@action }
            if (pw.length < 6) { pass.error = "Enter your password"; return@action }
            request({ api.signIn(e, pw) }) { r ->
                if (!r.ok) toast(r.message()) else {
                    token = r.json.optString("access_token"); refreshToken = r.json.optString("refresh_token"); email = e
                    name = r.json.optJSONObject("user")?.optJSONObject("user_metadata")?.optString("full_name") ?: "Student"
                    save(); dashboard()
                }
            }
        }
        link("Create student account") { register() }
        link("Forgot password?") { recover() }
        if (!api.configured) info("Supabase setup required", "Set SUPABASE_URL and SUPABASE_ANON_KEY in GitHub Actions secrets. Run supabase/schema.sql in your Supabase SQL Editor.")
    }

    private fun register() {
        screen("Create account", "Join PixelToonz Academy")
        val fullName = input("Full name")
        val mail = input("Email address")
        val pass = input("Password (at least 8 characters)", true)
        val confirm = input("Confirm password", true)
        action("Create account") {
            val n = fullName.text.toString().trim(); val e = mail.text.toString().trim(); val pw = pass.text.toString()
            when {
                n.length < 2 -> fullName.error = "Enter your name"
                !validEmail(e) -> mail.error = "Enter a valid email"
                pw.length < 8 -> pass.error = "Use at least 8 characters"
                pw != confirm.text.toString() -> confirm.error = "Passwords do not match"
                else -> request({ api.signUp(n, e, pw) }) { r ->
                    if (!r.ok) toast(r.message()) else {
                        val t = r.json.optString("access_token")
                        if (t.isNotBlank()) { token = t; refreshToken = r.json.optString("refresh_token"); email = e; name = n; save(); dashboard() }
                        else { toast("Account created. Check your email, then sign in."); login() }
                    }
                }
            }
        }
        link("Already registered? Sign in") { login() }
    }

    private fun recover() {
        screen("Reset password", "Enter your email to receive a recovery link.")
        val mail = input("Email address")
        action("Send recovery email") {
            val e = mail.text.toString().trim()
            if (!validEmail(e)) mail.error = "Enter a valid email"
            else request({ api.recover(e) }) { r ->
                if (r.ok) { toast("If the account exists, a recovery email has been sent."); login() } else toast(r.message())
            }
        }
        link("Back to sign in") { login() }
    }

    private fun dashboard() {
        screen("Hello, ${name.ifBlank { "Student" }} 👋", "Welcome to PixelToonz Academy")
        info("Your learning dashboard", "Explore courses, watch lessons and track your progress.")
        label("Latest announcements", 20, Color.WHITE, true)
        request({ api.announcements(token) }) { r ->
            if (r.ok) {
                val a = SupabaseClient.array(r.body)
                for (i in 0 until a.length()) {
                    val item = a.optJSONObject(i) ?: continue
                    info(item.optString("title"), item.optString("body"))
                }
            }
        }
        label("Explore courses", 20, Color.WHITE, true)
        request({ api.courses(token) }) { r ->
            if (!r.ok) { info("Could not load courses", r.message()); return@request }
            val a = SupabaseClient.array(r.body)
            if (a.length() == 0) info("No courses yet", "Published courses will appear here.")
            for (i in 0 until a.length()) {
                val c = a.optJSONObject(i) ?: continue
                val price = if (c.optBoolean("is_free")) "Free" else "₹" + c.optString("price")
                info(c.optString("title"), "${c.optString("level")} • $price\n${c.optString("description")}") { course(c) }
            }
        }
        request({ api.profile(token) }) { auth ->
            if (auth.ok) {
                userId = auth.json.optString("id")
                request({ api.profileRow(userId, token) }) { row ->
                    val profile = SupabaseClient.array(row.body).optJSONObject(0)
                    userRole = profile?.optString("role") ?: "student"
                    name = profile?.optString("full_name")?.ifBlank { name } ?: name
                    if (userRole == "admin" || userRole == "instructor") {
                        action(if (userRole == "admin") "Admin dashboard" else "Instructor dashboard") { staffDashboard() }
                    }
                }
            }
        }
        action("My enrollments") { enrollments() }
        action("My profile") { profile() }
        link("Sign out") { signOut() }
    }

    private fun course(c: JSONObject) {
        screen(c.optString("title", "Course"), c.optString("level", "All levels"))
        label(c.optString("description"), 15, muted)
        action(if (c.optBoolean("is_free")) "Enroll in course" else "Request enrollment") {
            request({ api.enroll(c.optString("id"), token) }) { r ->
                if (r.ok) toast("Enrollment saved") else toast(r.message())
            }
        }
        label("Lessons", 20, Color.WHITE, true)
        request({ api.lessons(c.optString("id"), token) }) { r ->
            if (!r.ok) { info("Lessons unavailable", r.message()); return@request }
            val lessons = SupabaseClient.array(r.body)
            if (lessons.length() == 0) info("Coming soon", "The instructor has not published lessons.")
            for (i in 0 until lessons.length()) {
                val l = lessons.optJSONObject(i) ?: continue
                info("${i + 1}. ${l.optString("title")}", "${l.optString("description")} • ${l.optInt("duration_minutes")} min") {
                    val url = l.optString("video_url")
                    if (url.startsWith("https://") || url.startsWith("http://")) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    else toast("Lesson video not available")
                    request({ api.markComplete(l.optString("id"), token) }) { p -> if (p.ok) toast("Progress saved") }
                }
            }
        }
        link("Back to dashboard") { dashboard() }
    }

    private fun enrollments() {
        screen("My enrollments", "Courses you have joined")
        request({ api.myEnrollments(token) }) { r ->
            if (!r.ok) info("Unable to load", r.message()) else {
                val a = SupabaseClient.array(r.body)
                if (a.length() == 0) info("No enrollments", "Explore courses to get started.")
                for (i in 0 until a.length()) info("Enrollment ${i + 1}", "Course ID: ${a.optJSONObject(i)?.optString("course_id")}")
            }
        }
        link("Back to dashboard") { dashboard() }
    }

    private fun profile() {
        screen("My profile", "Your student account")
        info("Name", name); info("Email", email)
        request({ api.profile(token) }) { r ->
            if (r.ok) {
                val u = r.json
                name = u.optJSONObject("user_metadata")?.optString("full_name") ?: name
                info("User ID", u.optString("id"))
            }
        }
        link("Back to dashboard") { dashboard() }
        link("Sign out") { signOut() }
    }


    private fun staffDashboard() {
        screen(if (userRole == "admin") "Admin dashboard" else "Instructor dashboard", "Role: $userRole")
        info("Course management", "Create and manage course drafts. Publishing is controlled by Supabase policies.")
        action("Create course") {
            screen("Create course", "New courses are saved as drafts.")
            val title = input("Course title")
            val description = input("Course description")
            val price = input("Price in INR (0 for free)")
            val free = CheckBox(this).apply { text = "Free course"; setTextColor(Color.WHITE); isChecked = true }
            page.addView(free)
            action("Save draft") {
                val amount = price.text.toString().toDoubleOrNull() ?: 0.0
                if (title.text.toString().trim().length < 3) title.error = "Enter a course title"
                else request({ api.createCourse(title.text.toString().trim(), description.text.toString(), free.isChecked, amount, userId, token) }) { result ->
                    if (result.ok) { toast("Course draft created"); staffDashboard() } else toast(result.message())
                }
            }
            link("Back") { staffDashboard() }
        }
        link("Back to dashboard") { dashboard() }
    }

    private fun request(call: () -> SupabaseClient.ApiResult, done: (SupabaseClient.ApiResult) -> Unit) {
        io.execute {
            val result = runCatching { call() }.getOrElse { SupabaseClient.ApiResult(0, it.message ?: "Network error") }
            runOnUiThread { if (!isFinishing) done(result) }
        }
    }

    private fun save() {
        getSharedPreferences("session", MODE_PRIVATE).edit().putString("token", token).putString("email", email).putString("name", name).apply()
    }
    private fun signOut() { token = ""; getSharedPreferences("session", MODE_PRIVATE).edit().clear().apply(); login() }
    private fun validEmail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
}
