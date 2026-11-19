package com.mantra.remote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.zip.Deflater
import kotlin.concurrent.thread
import kotlin.math.hypot

/**
 * v64 (Marko, 19.11.2026: "have built in server and viewer for VNC inside the app ... The user does not need to install
 * anything"): this phone's own VNC server, pure Kotlin, no native code.
 *
 * RFB 3.8 (3.3 and 3.7 viewers too) on port 5900, every interface (Tailscale peers and the home Wi-Fi reach it), VNC
 * password authentication only (security type 2; 5 wrong passwords block that address for a minute). The picture comes
 * from [ScreenShareService] (MediaProjection → VirtualDisplay → ImageReader, RGBA). Encodings: ZRLE (zlib, solid and
 * palette tiles) and Raw, any true-colour pixel format the viewer asks for (8, 16, 32 bit), DesktopSize (-223) when the
 * phone turns. Only the 64x64 tiles that changed since the last picture go out.
 *
 * Input: a viewer's mouse and keys act through Claude Control (the app's AccessibilityService): a click = tap, a held
 * click = long press, a drag = swipe, the right button = BACK, the wheel = scroll, Esc = BACK, Home = HOME, typed
 * characters go into the focused field. While Claude Control is off the server is VIEW ONLY (said in the app).
 */
object VncServer {
    const val PORT = 5900

    /** For the app's card: sharing on, viewers connected, last note. Written from any thread. */
    var on by mutableStateOf(false)
    var clients by mutableIntStateOf(0)
    var note by mutableStateOf("")

    // the picture: tightly packed RGBA, guarded by [lock]
    internal val lock = Object()
    internal var fbW = 0
    internal var fbH = 0
    internal var fb = ByteArray(0)
    internal var seq = 0L

    @Volatile var password = ""
    @Volatile private var server: ServerSocket? = null
    private val conns = CopyOnWriteArrayList<Conn>()
    private val failures = ConcurrentHashMap<String, LongArray>()   // address → [count, blocked until]
    internal val input = Executors.newSingleThreadExecutor()

    fun hasViewers() = conns.isNotEmpty()

    @Synchronized
    fun start(pw: String) {
        password = pw
        if (server != null) return
        val s = runCatching { ServerSocket(PORT, 8, InetAddress.getByName("0.0.0.0")).apply { reuseAddress = true } }
            .onFailure { note = "PORT $PORT IS TAKEN: ${it.message}"; android.util.Log.w("MantraRemote", "vnc server: $it") }
            .getOrNull() ?: return
        server = s
        thread(name = "vnc-accept", isDaemon = true) {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                thread(name = "vnc-client", isDaemon = true) { serve(c) }
            }
        }
        android.util.Log.i("MantraRemote", "vnc server listening on $PORT")
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }; server = null
        conns.forEach { it.close() }; conns.clear(); clients = 0
        synchronized(lock) { fbW = 0; fbH = 0; fb = ByteArray(0); seq++; lock.notifyAll() }
    }

    /** A new picture from the capture: [buffer] holds rows of [rowStride] bytes, 4 bytes a pixel (RGBA). */
    fun putFrame(w: Int, h: Int, buffer: ByteBuffer, rowStride: Int) {
        synchronized(lock) {
            if (w != fbW || h != fbH || fb.size != w * h * 4) { fbW = w; fbH = h; fb = ByteArray(w * h * 4) }
            val row = w * 4
            if (rowStride == row) { buffer.position(0); buffer.get(fb, 0, row * h) }
            else for (y in 0 until h) { buffer.position(y * rowStride); buffer.get(fb, y * row, row) }
            seq++
            lock.notifyAll()
        }
    }

    private fun blocked(ip: String): Boolean = (failures[ip]?.get(1) ?: 0L) > System.currentTimeMillis()
    private fun failed(ip: String) {
        val f = failures.getOrPut(ip) { LongArray(2) }
        f[0]++
        if (f[0] >= 5) { f[0] = 0; f[1] = System.currentTimeMillis() + 60_000 }
    }

    private fun serve(sock: Socket) {
        val c = Conn(sock)
        try {
            if (!c.handshake()) return
            conns += c; clients = conns.size
            note = "${sock.inetAddress.hostAddress} CONNECTED"
            thread(name = "vnc-send", isDaemon = true) { runCatching { c.sendLoop() }; c.close() }
            c.readLoop()
        } catch (e: Exception) {
            android.util.Log.i("MantraRemote", "vnc client ${sock.inetAddress.hostAddress}: $e")
        } finally {
            c.close()
            if (conns.remove(c)) { clients = conns.size; note = "${sock.inetAddress.hostAddress} LEFT" }
        }
    }

    /** The pixel format a viewer asked for (true colour only). */
    private class Pf(
        val bpp: Int, val depth: Int, val bigEndian: Boolean,
        val rMax: Int, val gMax: Int, val bMax: Int, val rShift: Int, val gShift: Int, val bShift: Int,
    ) {
        val bytes = bpp / 8
        val rTab = IntArray(256) { ((it * rMax + 127) / 255) shl rShift }
        val gTab = IntArray(256) { ((it * gMax + 127) / 255) shl gShift }
        val bTab = IntArray(256) { ((it * bMax + 127) / 255) shl bShift }
        /** The pixel bytes straight from RGBA: 32 bit little endian, R at 0, G at 8, B at 16. */
        val native = bpp == 32 && !bigEndian && rMax == 255 && gMax == 255 && bMax == 255 && rShift == 0 && gShift == 8 && bShift == 16
        private val all = (rMax shl rShift) or (gMax shl gShift) or (bMax shl bShift)
        /** ZRLE's CPIXEL: 3 bytes when a 32 bit pixel uses only its low or its high 24 bits. */
        val cLow = bpp == 32 && depth <= 24 && (all.toLong() and 0xffffffffL) <= 0xffffffL
        val cHigh = bpp == 32 && depth <= 24 && !cLow && (all and 0xff) == 0
        val cbytes = if (cLow || cHigh) 3 else bytes

        companion object {
            val DEFAULT = Pf(32, 24, false, 255, 255, 255, 0, 8, 16)
        }
    }

    /** A growable byte buffer, reused for every rectangle. */
    private class Buf {
        var data = ByteArray(1 shl 16)
        var size = 0
        fun need(n: Int) { if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n)) }
        fun put(b: Int) { need(1); data[size++] = b.toByte() }
    }

    private class Conn(val sock: Socket) {
        val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), 1 shl 14))
        val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 1 shl 16))
        @Volatile var pf = Pf.DEFAULT
        @Volatile var zrle = false
        @Volatile var desktopSize = false
        @Volatile var alive = true
        // guarded by VncServer.lock
        var pendFull: IntArray? = null
        var pendIncr: IntArray? = null
        var w = 0; var h = 0
        var prev = ByteArray(0)
        var lastSeq = -1L
        private val deflater = Deflater(6)
        private val buf = Buf()
        private var zout = ByteArray(1 shl 16)
        private val ip = sock.inetAddress.hostAddress ?: "?"
        // pointer
        private var mask = 0
        private var downX = 0; private var downY = 0; private var downT = 0L; private var moved = 0.0

        fun close() {
            alive = false
            runCatching { sock.close() }
            synchronized(lock) { lock.notifyAll() }
        }

        private fun writeString(s: String) { val b = s.toByteArray(); out.writeInt(b.size); out.write(b) }

        fun handshake(): Boolean {
            sock.tcpNoDelay = true
            sock.soTimeout = 30_000
            out.write("RFB 003.008\n".toByteArray()); out.flush()
            val v = ByteArray(12); inp.readFully(v)
            val minor = String(v).substring(8, 11).toIntOrNull() ?: 8
            if (blocked(ip)) {
                if (minor >= 7) out.writeByte(0) else out.writeInt(0)
                writeString("Too many wrong passwords: wait a minute"); out.flush(); return false
            }
            val pw = password
            // v64 (Marko, 19.11.2026: "custom password without any security limitations. It can be even one one
            // number"): any length; empty = no password at all (security type None)
            val type = if (pw.isEmpty()) 1 else 2
            if (minor >= 7) {
                out.writeByte(1); out.writeByte(type); out.flush()
                if (inp.readUnsignedByte() != type) return false
            } else out.writeInt(type)
            if (type == 2) {
                val challenge = ByteArray(16).also { SecureRandom().nextBytes(it) }
                out.write(challenge); out.flush()
                val resp = ByteArray(16); inp.readFully(resp)
                if (!MessageDigest.isEqual(resp, Rfb.vncResponse(pw, challenge))) {
                    failed(ip)
                    note = "$ip: WRONG PASSWORD"
                    out.writeInt(1); if (minor >= 8) writeString("Wrong password"); out.flush(); return false
                }
                failures.remove(ip)
                out.writeInt(0)
            } else if (minor >= 8) out.writeInt(0)   // None: a SecurityResult only in 3.8
            out.flush()
            inp.readUnsignedByte()   // ClientInit: shared or not, every viewer shares
            synchronized(lock) {
                val until = System.currentTimeMillis() + 5000
                while (fbW == 0 && System.currentTimeMillis() < until) lock.wait(500)
                w = fbW; h = fbH; prev = ByteArray(w * h * 4)
            }
            if (w == 0) { note = "NO PICTURE YET"; return false }
            sock.soTimeout = 0
            out.writeShort(w); out.writeShort(h)
            out.writeByte(32); out.writeByte(24); out.writeByte(0); out.writeByte(1)
            out.writeShort(255); out.writeShort(255); out.writeShort(255)
            out.writeByte(0); out.writeByte(8); out.writeByte(16); out.write(ByteArray(3))
            writeString("Mantra Remote Viewing · " + android.os.Build.MODEL)
            out.flush()
            return true
        }

        private fun skip(n: Int) { if (n > 0) inp.readFully(ByteArray(n)) }

        fun readLoop() {
            while (alive) {
                when (inp.readUnsignedByte()) {
                    0 -> {                                     // SetPixelFormat
                        skip(3)
                        val bpp = inp.readUnsignedByte(); val depth = inp.readUnsignedByte()
                        val be = inp.readUnsignedByte() != 0; val tc = inp.readUnsignedByte() != 0
                        val rm = inp.readUnsignedShort(); val gm = inp.readUnsignedShort(); val bm = inp.readUnsignedShort()
                        val rs = inp.readUnsignedByte(); val gs = inp.readUnsignedByte(); val bs = inp.readUnsignedByte()
                        skip(3)
                        if (tc && (bpp == 8 || bpp == 16 || bpp == 32) && rm > 0 && gm > 0 && bm > 0) pf = Pf(bpp, depth, be, rm, gm, bm, rs, gs, bs)
                        else android.util.Log.w("MantraRemote", "vnc: pixel format bpp=$bpp tc=$tc not served, kept 32 bit")
                    }
                    2 -> {                                     // SetEncodings
                        skip(1)
                        val n = inp.readUnsignedShort()
                        val list = IntArray(n) { inp.readInt() }
                        desktopSize = -223 in list
                        // the first of ZRLE and Raw in the viewer's order
                        zrle = list.firstOrNull { it == 16 || it == 0 } == 16
                    }
                    3 -> {                                     // FramebufferUpdateRequest
                        val incr = inp.readUnsignedByte() != 0
                        val r = intArrayOf(inp.readUnsignedShort(), inp.readUnsignedShort(), inp.readUnsignedShort(), inp.readUnsignedShort())
                        synchronized(lock) {
                            if (incr) pendIncr = union(pendIncr, r) else pendFull = union(pendFull, r)
                            lock.notifyAll()
                        }
                    }
                    4 -> {                                     // KeyEvent
                        val down = inp.readUnsignedByte() != 0; skip(2); val sym = inp.readInt()
                        if (down) key(sym)
                    }
                    5 -> {                                     // PointerEvent
                        val m = inp.readUnsignedByte(); val x = inp.readUnsignedShort(); val y = inp.readUnsignedShort()
                        pointer(m, x, y)
                    }
                    6 -> { skip(3); skip(inp.readInt()) }      // ClientCutText: not taken
                    else -> return
                }
            }
        }

        private fun union(a: IntArray?, b: IntArray): IntArray {
            if (a == null) return b
            val x0 = minOf(a[0], b[0]); val y0 = minOf(a[1], b[1])
            val x1 = maxOf(a[0] + a[2], b[0] + b[2]); val y1 = maxOf(a[1] + a[3], b[1] + b[3])
            return intArrayOf(x0, y0, x1 - x0, y1 - y0)
        }

        // ------------------------------------------------------------ sending

        fun sendLoop() {
            while (alive) {
                var rects: List<IntArray> = emptyList()
                var resized = false
                synchronized(lock) {
                    while (alive) {
                        if (fbW == 0) { lock.wait(1000); continue }
                        if (fbW != w || fbH != h) {
                            if (!desktopSize) { note = "$ip: VIEWER CANNOT FOLLOW A NEW SIZE"; alive = false; return }
                            w = fbW; h = fbH; prev = ByteArray(w * h * 4)
                            resized = true; pendFull = intArrayOf(0, 0, w, h)
                        }
                        val full = pendFull
                        if (full != null) {
                            val r = clip(full)
                            if (r != null) copyToPrev(r[0], r[1], r[2], r[3])
                            rects = listOfNotNull(r); pendFull = null; lastSeq = seq
                            break
                        }
                        val incr = pendIncr
                        if (incr != null && seq != lastSeq) {
                            lastSeq = seq
                            val d = clip(incr)?.let { diff(it) } ?: emptyList()
                            if (d.isNotEmpty()) { rects = d; pendIncr = null; break }
                        }
                        lock.wait(1000)
                    }
                }
                if (!alive) return
                out.writeByte(0); out.writeByte(0); out.writeShort(rects.size + if (resized) 1 else 0)
                if (resized) { out.writeShort(0); out.writeShort(0); out.writeShort(w); out.writeShort(h); out.writeInt(-223) }
                for (r in rects) {
                    out.writeShort(r[0]); out.writeShort(r[1]); out.writeShort(r[2]); out.writeShort(r[3])
                    if (zrle) { out.writeInt(16); zrleRect(r[0], r[1], r[2], r[3]) } else { out.writeInt(0); rawRect(r[0], r[1], r[2], r[3]) }
                }
                out.flush()
            }
        }

        private fun clip(r: IntArray): IntArray? {
            val x0 = r[0].coerceIn(0, w); val y0 = r[1].coerceIn(0, h)
            val x1 = (r[0] + r[2]).coerceIn(0, w); val y1 = (r[1] + r[3]).coerceIn(0, h)
            return if (x1 > x0 && y1 > y0) intArrayOf(x0, y0, x1 - x0, y1 - y0) else null
        }

        private fun copyToPrev(x: Int, y: Int, rw: Int, rh: Int) {
            for (row in y until y + rh) { val o = (row * w + x) * 4; System.arraycopy(fb, o, prev, o, rw * 4) }
        }

        /** The 64x64 tiles inside [r] that differ from what this viewer has; copied into [prev]; runs merged per row. */
        private fun diff(r: IntArray): List<IntArray> {
            val list = ArrayList<IntArray>()
            val x0 = r[0] / T * T; val y0 = r[1] / T * T; val x1 = r[0] + r[2]; val y1 = r[1] + r[3]
            var ty = y0
            while (ty < y1) {
                val th = minOf(T, h - ty)
                var runX = -1; var runEnd = 0
                var tx = x0
                while (tx < x1) {
                    val tw = minOf(T, w - tx)
                    var changed = false
                    for (row in ty until ty + th) {
                        val o = (row * w + tx) * 4
                        if (!java.util.Arrays.equals(fb, o, o + tw * 4, prev, o, o + tw * 4)) { changed = true; break }
                    }
                    if (changed) {
                        copyToPrev(tx, ty, tw, th)
                        if (runX < 0) runX = tx
                        runEnd = tx + tw
                    } else if (runX >= 0) { list += intArrayOf(runX, ty, runEnd - runX, th); runX = -1 }
                    tx += T
                }
                if (runX >= 0) list += intArrayOf(runX, ty, runEnd - runX, th)
                ty += T
            }
            return list
        }

        private fun value(o: Int, p: Pf): Int =
            p.rTab[prev[o].toInt() and 0xff] or p.gTab[prev[o + 1].toInt() and 0xff] or p.bTab[prev[o + 2].toInt() and 0xff]

        private fun putPixel(b: Buf, v: Int, p: Pf, n: Int) {
            b.need(4)
            val d = b.data; var s = b.size
            when {
                n == 3 && p.cHigh -> if (p.bigEndian) { d[s++] = (v ushr 24).toByte(); d[s++] = (v ushr 16).toByte(); d[s++] = (v ushr 8).toByte() }
                    else { d[s++] = (v ushr 8).toByte(); d[s++] = (v ushr 16).toByte(); d[s++] = (v ushr 24).toByte() }
                n == 3 -> if (p.bigEndian) { d[s++] = (v ushr 16).toByte(); d[s++] = (v ushr 8).toByte(); d[s++] = v.toByte() }
                    else { d[s++] = v.toByte(); d[s++] = (v ushr 8).toByte(); d[s++] = (v ushr 16).toByte() }
                n == 1 -> d[s++] = v.toByte()
                n == 2 -> if (p.bigEndian) { d[s++] = (v ushr 8).toByte(); d[s++] = v.toByte() } else { d[s++] = v.toByte(); d[s++] = (v ushr 8).toByte() }
                else -> if (p.bigEndian) { d[s++] = (v ushr 24).toByte(); d[s++] = (v ushr 16).toByte(); d[s++] = (v ushr 8).toByte(); d[s++] = v.toByte() }
                    else { d[s++] = v.toByte(); d[s++] = (v ushr 8).toByte(); d[s++] = (v ushr 16).toByte(); d[s++] = (v ushr 24).toByte() }
            }
            b.size = s
        }

        private fun rawRect(x: Int, y: Int, rw: Int, rh: Int) {
            val p = pf
            for (row in y until y + rh) {
                val o = (row * w + x) * 4
                if (p.native) { out.write(prev, o, rw * 4); continue }
                buf.size = 0
                for (i in 0 until rw) putPixel(buf, value(o + i * 4, p), p, p.bytes)
                out.write(buf.data, 0, buf.size)
            }
        }

        private val tile = IntArray(T * T)
        private val pal = IntArray(16)

        private fun zrleRect(x: Int, y: Int, rw: Int, rh: Int) {
            val p = pf
            buf.size = 0
            var ty = y
            while (ty < y + rh) {
                val th = minOf(T, y + rh - ty)
                var tx = x
                while (tx < x + rw) {
                    val tw = minOf(T, x + rw - tx)
                    zrleTile(tx, ty, tw, th, p)
                    tx += T
                }
                ty += T
            }
            deflater.setInput(buf.data, 0, buf.size)
            var total = 0
            while (true) {
                if (zout.size - total < 1024) zout = zout.copyOf(zout.size * 2)
                val space = zout.size - total
                val n = deflater.deflate(zout, total, space, Deflater.SYNC_FLUSH)
                total += n
                if (n < space) break
            }
            out.writeInt(total); out.write(zout, 0, total)
        }

        private fun zrleTile(tx: Int, ty: Int, tw: Int, th: Int, p: Pf) {
            var k = 0
            var n = 0
            for (row in ty until ty + th) {
                var o = (row * w + tx) * 4
                for (i in 0 until tw) {
                    val v = value(o, p); o += 4
                    tile[k++] = v
                    if (n <= 16) {
                        var found = false
                        for (j in 0 until minOf(n, 16)) if (pal[j] == v) { found = true; break }
                        if (!found) { if (n < 16) pal[n] = v; n++ }
                    }
                }
            }
            val cb = p.cbytes
            when {
                n == 1 -> { buf.put(1); putPixel(buf, pal[0], p, cb) }
                n <= 16 -> {
                    buf.put(n)
                    for (j in 0 until n) putPixel(buf, pal[j], p, cb)
                    val bits = if (n <= 2) 1 else if (n <= 4) 2 else 4
                    var idx = 0
                    for (row in 0 until th) {
                        var acc = 0; var used = 0
                        for (i in 0 until tw) {
                            val v = tile[idx++]
                            var pi = 0
                            while (pal[pi] != v) pi++
                            acc = (acc shl bits) or pi; used += bits
                            if (used == 8) { buf.put(acc); acc = 0; used = 0 }
                        }
                        if (used > 0) buf.put(acc shl (8 - used))
                    }
                }
                else -> {
                    buf.put(0)
                    for (i in 0 until k) putPixel(buf, tile[i], p, cb)
                }
            }
        }

        // ------------------------------------------------------------ input, through Claude Control

        private fun pointer(m: Int, x: Int, y: Int) {
            val was = mask; mask = m
            if (!ClaudeControl.isUp()) return
            val now = System.currentTimeMillis()
            val b1 = (m and 1) != 0; val w1 = (was and 1) != 0
            if (b1 && !w1) { downX = x; downY = y; downT = now; moved = 0.0 }
            if (b1) moved = maxOf(moved, hypot((x - downX).toDouble(), (y - downY).toDouble()))
            if (!b1 && w1) {
                val dur = now - downT
                val sx = downX; val sy = downY; val far = moved
                input.execute {
                    when {
                        far < 12 && dur >= 500 -> ClaudeControl.remoteLongPress(sx, sy, dur.toInt())
                        far < 12 -> ClaudeControl.remoteTap(sx, sy)
                        else -> ClaudeControl.remoteSwipe(sx, sy, x, y, dur.toInt().coerceIn(80, 1500))
                    }
                }
            }
            if ((m and 4) != 0 && (was and 4) == 0) input.execute { ClaudeControl.remoteGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }
            // the wheel: up = the page comes down (finger moves down), down = the finger moves up
            if ((m and 8) != 0 && (was and 8) == 0) input.execute { ClaudeControl.remoteSwipe(x, y, x, (y + h / 4).coerceAtMost(h - 1), 200) }
            if ((m and 16) != 0 && (was and 16) == 0) input.execute { ClaudeControl.remoteSwipe(x, y, x, (y - h / 4).coerceAtLeast(1), 200) }
        }

        private fun key(sym: Int) {
            if (!ClaudeControl.isUp()) return
            input.execute {
                when (sym) {
                    0xff1b -> ClaudeControl.remoteGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    0xff50 -> ClaudeControl.remoteGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    0xff67, 0xffbe -> ClaudeControl.remoteGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)  // Menu, F1
                    0xff08 -> ClaudeControl.remoteBackspace()
                    0xff0d, 0xff8d -> ClaudeControl.remoteEnter()
                    else -> {
                        val ch = when {
                            sym in 0x20..0x7e || sym in 0xa0..0xff -> String(Character.toChars(sym))
                            (sym and 0xff000000.toInt()) == 0x01000000 -> String(Character.toChars(sym and 0xffffff))
                            else -> null
                        }
                        if (ch != null) ClaudeControl.remoteText(ch)
                    }
                }
            }
        }
    }

    private const val T = 64
}
