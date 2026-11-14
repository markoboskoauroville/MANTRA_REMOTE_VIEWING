package com.mantra.remote

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * v51 (Marko, 14.11.2026: "I need to login with my remote viewing to this machine and on the gear icon I will have each
 * program and we now call the program services"): the VM's vmctl API (repo vm-chromium tools/vmctl, port 8896, Tailscale
 * only). GET /services lists them; POST /services/NAME/start|stop. Token: X-Mantra-Token, kept in the app's private prefs
 * under [TOKEN_PREF], never logged. Network only; call from a background dispatcher.
 */
object VmServices {
    const val PORT = 8896
    const val TOKEN_PREF = "vmctl_token"

    data class Service(val name: String, val short: String, val title: String, val core: Boolean, val running: Boolean)

    class Failure(msg: String) : Exception(msg)

    private fun call(ip: String, token: String, method: String, path: String, body: String? = null, readMs: Int = 15_000): JSONObject {
        val c = URL("http://$ip:$PORT$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 6_000
            c.readTimeout = readMs                       // a Pixel takes ~40-60 s to boot: start/stop wait long
            c.setRequestProperty("X-Mantra-Token", token)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val j = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code >= 400 || !j.optBoolean("ok", false)) {
                throw Failure(when (code) {
                    401 -> "WRONG OR MISSING TOKEN"
                    403 -> "LOCKED (CORE)"
                    else -> j.optString("error", "HTTP $code").uppercase().take(120)
                })
            }
            return j
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure("NO ANSWER FROM VMCTL (${e.javaClass.simpleName})")
        } finally {
            c.disconnect()
        }
    }

    fun list(ip: String, token: String): List<Service> {
        val a = call(ip, token, "GET", "/services").getJSONArray("services")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Service(o.getString("name"), o.optString("short", o.getString("name")).take(8), o.optString("title", ""),
                o.optBoolean("core"), o.optBoolean("running"))
        }
    }

    /** Start (gui = the gear's start: a phone with its mirror on the VM screen) or stop one service. */
    fun set(ip: String, token: String, name: String, on: Boolean) {
        val body = JSONObject().put("who", "remote-viewing-android").put("gui", on).toString()
        call(ip, token, "POST", "/services/$name/${if (on) "start" else "stop"}", body, readMs = 300_000)
    }
}
