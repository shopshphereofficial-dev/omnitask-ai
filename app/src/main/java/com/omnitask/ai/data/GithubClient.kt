package com.omnitask.ai.data

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client for the GitHub REST API, used by the github_* actions so the
 * assistant can create repositories, push files and trigger builds.
 *
 * Every call needs a personal access token; the token is stored (encrypted)
 * in the app's Settings screen. All calls are blocking - run them off the
 * main thread.
 */
object GithubClient {

    private const val API = "https://api.github.com"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(token: String, path: String, method: String, body: JSONObject?): Request {
        val b = Request.Builder()
            .url(API + path)
            .addHeader("Authorization", "Bearer " + token.trim())
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("User-Agent", "OmniTask-AI")
        when (method) {
            "POST" -> b.post((body ?: JSONObject()).toString().toRequestBody(JSON))
            "PUT" -> b.put((body ?: JSONObject()).toString().toRequestBody(JSON))
            "PATCH" -> b.patch((body ?: JSONObject()).toString().toRequestBody(JSON))
            else -> b.get()
        }
        return b.build()
    }

    private fun call(token: String, path: String, method: String = "GET", body: JSONObject? = null): String {
        if (token.isBlank()) {
            throw RuntimeException("No GitHub token set - open Settings > GitHub and paste a token, then try again")
        }
        client.newCall(request(token, path, method, body)).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("GitHub HTTP ${resp.code}: " + text.take(300))
            }
            return text
        }
    }

    /** Returns the logged-in user's login name. */
    fun login(token: String): String {
        return JSONObject(call(token, "/user")).optString("login")
    }

    /** All repositories for the user, "owner/name", most recently updated first. */
    fun listRepos(token: String): List<String> {
        val arr = JSONArray(call(token, "/user/repos?per_page=100&sort=updated"))
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            out.add(arr.getJSONObject(i).optString("full_name"))
        }
        return out
    }

    fun createRepo(token: String, name: String, privateRepo: Boolean, description: String): String {
        val body = JSONObject()
            .put("name", name.trim())
            .put("private", privateRepo)
            .put("description", description)
            .put("auto_init", true)
        val o = JSONObject(call(token, "/user/repos", "POST", body))
        return o.optString("full_name")
    }

    fun getFile(token: String, repo: String, path: String): String {
        val o = JSONObject(call(token, "/repos/" + repo.trim() + "/contents/" + path.trim()))
        val b64 = o.optString("content").replace("\n", "").replace("\r", "")
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        return String(bytes, Charsets.UTF_8)
    }

    private fun fileSha(token: String, repo: String, path: String): String? {
        return try {
            val o = JSONObject(call(token, "/repos/" + repo.trim() + "/contents/" + path.trim()))
            o.optString("sha").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /** Creates or updates a file. Returns a short confirmation. */
    fun putFile(
        token: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String = "main"
    ): String {
        val body = JSONObject()
            .put("message", message.ifBlank { "Update " + path })
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .put("branch", branch)
        val sha = fileSha(token, repo, path)
        if (sha != null) body.put("sha", sha)
        val o = JSONObject(call(token, "/repos/" + repo.trim() + "/contents/" + path.trim(), "PUT", body))
        val url = o.optJSONObject("commit")?.optString("html_url").orEmpty()
        return if (url.isBlank()) "Saved " + path else "Saved " + path + " - " + url
    }

    /** Starts the repository's Android build workflow and returns a link. */
    fun dispatchBuild(token: String, repo: String, workflow: String = "android-build.yml", ref: String = "main"): String {
        call(
            token,
            "/repos/" + repo.trim() + "/actions/workflows/" + workflow + "/dispatches",
            "POST",
            JSONObject().put("ref", ref)
        )
        return "Build started on " + repo.trim() + " - open the Actions tab in a minute to watch it"
    }

    /** Human-readable status of the most recent workflow run. */
    fun latestRunStatus(token: String, repo: String): String {
        val arr = JSONObject(call(token, "/repos/" + repo.trim() + "/actions/runs?per_page=1"))
            .optJSONArray("workflow_runs") ?: JSONArray()
        if (arr.length() == 0) return "No builds have run yet for " + repo.trim()
        val r = arr.getJSONObject(0)
        val status = r.optString("status")
        val conclusion = r.optString("conclusion")
        val label = if (status == "completed") conclusion.ifBlank { "unknown" } else status
        val url = r.optString("html_url")
        return "Latest build: " + label + " - " + url
    }

    /** Download link for the newest APK attached to a release, if any. */
    fun latestApkUrl(token: String, repo: String): String? {
        val arr = JSONArray(call(token, "/repos/" + repo.trim() + "/releases?per_page=5"))
        for (i in 0 until arr.length()) {
            val assets = arr.getJSONObject(i).optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name").endsWith(".apk")) return a.optString("browser_download_url")
            }
        }
        return null
    }
}
