package com.mantra.remote

import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Our own RFB (VNC) client, as FEATURE_REQUEST.md PART 2B decided: the app, not a library, decides when a frame is
 * asked for and for which rectangle. That is how one monitor of two is watched without paying for the other.
 *
 * Implemented: RFB 3.8 handshake, security None (1) and VNC password (2), ServerInit, SetPixelFormat (32 bit true
 * colour), the decoders Raw, CopyRect, Hextile, Zlib and ZRLE, the DesktopSize pseudo-encoding, PointerEvent and
 * KeyEvent. The Mac side is Apple's own Screen Sharing with "VNC viewers may control screen with password" ticked.
 */
class Rfb(
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected(name: String, width: Int, height: Int)
        fun onFrame()                       // pixels changed in the bitmap
        fun onClosed(reason: String)
        fun onStats(bytesIn: Long, frames: Long, lastFrameMs: Long)
    }

    @Volatile var bitmap: Bitmap? = null; private set
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set

    /** The rectangle we ask frames for (the chosen monitor), in framebuffer pixels. */
    @Volatile var region = IntArray(4)
    /** Minimum time between two frame requests. 0 = as fast as the link allows. */
    @Volatile var minIntervalMs = 100L
    /** When true no frames are asked for at all (input still works). */
    @Volatile var frozen = false

    private var socket: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var running = false
    private var pixels = IntArray(0)
    private val zlibInflater = Inflater()
    private val zrleInflater = Inflater()
    private var bytesIn = 0L
    private var frames = 0L
    @Volatile private var fullRequestPending = true
    private val requestLock = Object()

    fun connect(host: String, port: Int, password: String) {
        running = true
        Thread({
            try {
                run(host, port, password)
            } catch (e: Exception) {
                if (running) listener.onClosed(e.message ?: e.javaClass.simpleName)
            } finally {
                running = false
                runCatching { socket?.close() }
            }
        }, "rfb").start()
    }

    fun close() {
        running = false
        runCatching { socket?.close() }
        synchronized(requestLock) { requestLock.notifyAll() }
    }

    /** The region changed (another monitor): ask for the whole of it once, not just what changed. */
    fun setRegion(x: Int, y: Int, w: Int, h: Int) {
        region = intArrayOf(x, y, w, h)
        fullRequestPending = true
        synchronized(requestLock) { requestLock.notifyAll() }
    }

    fun wake() {
        fullRequestPending = true
        synchronized(requestLock) { requestLock.notifyAll() }
    }

    // ---------------------------------------------------------------- input, sent from the UI thread

    fun pointer(x: Int, y: Int, buttons: Int) = send {
        it.writeByte(5); it.writeByte(buttons)
        it.writeShort(x.coerceIn(0, (width - 1).coerceAtLeast(0)))
        it.writeShort(y.coerceIn(0, (height - 1).coerceAtLeast(0)))
    }

    fun key(keysym: Int, down: Boolean) = send {
        it.writeByte(4); it.writeByte(if (down) 1 else 0); it.writeShort(0); it.writeInt(keysym)
    }

    private fun send(block: (DataOutputStream) -> Unit) {
        if (!running) return
        writer.execute {
            runCatching { synchronized(output) { block(output); output.flush() } }
        }
    }

    // ---------------------------------------------------------------- the session

    private fun run(host: String, port: Int, password: String) {
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(host, port), 10_000)
        s.soTimeout = 0
        socket = s
        input = DataInputStream(BufferedInputStream(s.getInputStream(), 1 shl 16))
        output = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 1 shl 14))

        // ProtocolVersion
        val ver = ByteArray(12); input.readFully(ver)
        val serverVersion = String(ver).trim()
        if (!serverVersion.startsWith("RFB ")) throw IllegalStateException("NOT A VNC SERVER")
        output.write("RFB 003.008\n".toByteArray()); output.flush()

        // Security
        val n = input.readUnsignedByte()
        if (n == 0) throw IllegalStateException("REFUSED: " + readString())
        val types = ByteArray(n).also { input.readFully(it) }.map { it.toInt() and 0xff }
        when {
            2 in types -> {
                output.writeByte(2); output.flush()
                val challenge = ByteArray(16); input.readFully(challenge)
                output.write(vncResponse(password, challenge)); output.flush()
            }
            1 in types -> { output.writeByte(1); output.flush() }
            else -> throw IllegalStateException(
                "NO VNC PASSWORD ON THE MAC (server offers $types). Tick \"VNC viewers may control screen with password\".")
        }
        if (input.readInt() != 0) throw IllegalStateException("PASSWORD REFUSED: " + runCatching { readString() }.getOrDefault(""))

        // ClientInit (shared, so the Mac's own screen stays as it is) and ServerInit
        output.writeByte(1); output.flush()
        val w = input.readUnsignedShort(); val h = input.readUnsignedShort()
        input.skipBytes(16)                       // the server's pixel format: we set our own below
        val name = readString()
        resize(w, h)

        // SetPixelFormat: 32 bpp, depth 24, little endian, true colour, R<<16 G<<8 B, so a pixel is an ARGB int
        output.writeByte(0); output.write(ByteArray(3))
        output.writeByte(32); output.writeByte(24); output.writeByte(0); output.writeByte(1)
        output.writeShort(255); output.writeShort(255); output.writeShort(255)
        output.writeByte(16); output.writeByte(8); output.writeByte(0); output.write(ByteArray(3))
        // SetEncodings: ZRLE, Zlib, Hextile, CopyRect, Raw, DesktopSize
        val enc = intArrayOf(16, 6, 5, 1, 0, -223)
        output.writeByte(2); output.writeByte(0); output.writeShort(enc.size)
        enc.forEach { output.writeInt(it) }
        output.flush()

        // no frame is asked for until the screen has said which monitor: one monitor costs less than two
        listener.onConnected(name, w, h)

        Thread({ requestLoop() }, "rfb-requests").start()
        readLoop()
    }

    /** Ask for the next frame of the region when the previous one has arrived and the interval has passed. */
    @Volatile private var awaitingFrame = false
    @Volatile private var lastRequest = 0L

    private fun requestLoop() {
        while (running) {
            synchronized(requestLock) {
                val wait = when {
                    frozen && !fullRequestPending -> 1000L
                    awaitingFrame && System.currentTimeMillis() - lastRequest < 5000 -> 1000L
                    else -> (lastRequest + minIntervalMs - System.currentTimeMillis()).coerceAtLeast(0)
                }
                if (wait > 0) { requestLock.wait(wait); return@synchronized }
                if (frozen && !fullRequestPending) return@synchronized
                if (region[2] == 0) { requestLock.wait(200); return@synchronized }
                val r = region
                val incremental = !fullRequestPending
                fullRequestPending = false
                awaitingFrame = true
                lastRequest = System.currentTimeMillis()
                send {
                    it.writeByte(3); it.writeByte(if (incremental) 1 else 0)
                    it.writeShort(r[0]); it.writeShort(r[1]); it.writeShort(r[2]); it.writeShort(r[3])
                }
            }
        }
    }

    private fun readLoop() {
        while (running) {
            when (val type = input.readUnsignedByte()) {
                0 -> framebufferUpdate()
                1 -> {                                         // SetColourMapEntries: not used in true colour
                    input.skipBytes(3); val count = input.readUnsignedShort(); input.skipBytes(count * 6)
                }
                2 -> Unit                                      // Bell
                3 -> { input.skipBytes(3); val len = input.readInt(); input.skipBytes(len) } // ServerCutText
                else -> throw IllegalStateException("UNKNOWN MESSAGE $type")
            }
        }
    }

    private fun framebufferUpdate() {
        val t0 = System.currentTimeMillis()
        input.skipBytes(1)
        val rects = input.readUnsignedShort()
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = 0; var maxY = 0
        for (i in 0 until rects) {
            val x = input.readUnsignedShort(); val y = input.readUnsignedShort()
            val w = input.readUnsignedShort(); val h = input.readUnsignedShort()
            when (val e = input.readInt()) {
                0 -> raw(x, y, w, h)
                1 -> copyRect(x, y, w, h)
                5 -> hextile(x, y, w, h)
                6 -> zlib(x, y, w, h)
                16 -> zrle(x, y, w, h)
                -223 -> { resize(w, h); listener.onConnected("", w, h); continue }
                else -> throw IllegalStateException("UNKNOWN ENCODING $e")
            }
            minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x + w); maxY = maxOf(maxY, y + h)
        }
        if (maxX > minX && maxY > minY) {
            bitmap?.setPixels(pixels, minY * width + minX, width, minX, minY, maxX - minX, maxY - minY)
        }
        frames++
        awaitingFrame = false
        synchronized(requestLock) { requestLock.notifyAll() }
        listener.onFrame()
        listener.onStats(bytesIn, frames, System.currentTimeMillis() - lastRequest.coerceAtMost(t0))
    }

    private fun resize(w: Int, h: Int) {
        width = w; height = h
        pixels = IntArray(w * h)
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    // ---------------------------------------------------------------- decoders

    private fun readFully(n: Int): ByteArray {
        val b = ByteArray(n); input.readFully(b); bytesIn += n; return b
    }

    private fun raw(x: Int, y: Int, w: Int, h: Int) = rawFrom(readFully(w * h * 4), 0, x, y, w, h)

    private fun rawFrom(b: ByteArray, off: Int, x: Int, y: Int, w: Int, h: Int) {
        var p = off
        for (row in 0 until h) {
            var i = (y + row) * width + x
            for (col in 0 until w) {
                pixels[i++] = argb(b, p); p += 4
            }
        }
    }

    private fun argb(b: ByteArray, p: Int): Int =
        (0xff shl 24) or ((b[p + 2].toInt() and 0xff) shl 16) or ((b[p + 1].toInt() and 0xff) shl 8) or (b[p].toInt() and 0xff)

    private fun copyRect(x: Int, y: Int, w: Int, h: Int) {
        val sx = input.readUnsignedShort(); val sy = input.readUnsignedShort(); bytesIn += 4
        val tmp = IntArray(w * h)
        for (row in 0 until h) System.arraycopy(pixels, (sy + row) * width + sx, tmp, row * w, w)
        for (row in 0 until h) System.arraycopy(tmp, row * w, pixels, (y + row) * width + x, w)
    }

    private fun fill(x: Int, y: Int, w: Int, h: Int, c: Int) {
        for (row in y until y + h) java.util.Arrays.fill(pixels, row * width + x, row * width + x + w, c)
    }

    private fun pixel(): Int = argb(readFully(4), 0)

    private fun hextile(x: Int, y: Int, w: Int, h: Int) {
        var bg = 0xff000000.toInt(); var fg = 0xffffffff.toInt()
        var ty = y
        while (ty < y + h) {
            val th = minOf(16, y + h - ty)
            var tx = x
            while (tx < x + w) {
                val tw = minOf(16, x + w - tx)
                val mask = input.readUnsignedByte(); bytesIn++
                if (mask and 1 != 0) {
                    raw(tx, ty, tw, th)
                } else {
                    if (mask and 2 != 0) bg = pixel()
                    fill(tx, ty, tw, th, bg)
                    if (mask and 4 != 0) fg = pixel()
                    if (mask and 8 != 0) {
                        val count = input.readUnsignedByte(); bytesIn++
                        val coloured = mask and 16 != 0
                        repeat(count) {
                            val c = if (coloured) pixel() else fg
                            val xy = input.readUnsignedByte(); val wh = input.readUnsignedByte(); bytesIn += 2
                            fill(tx + (xy shr 4), ty + (xy and 15), (wh shr 4) + 1, (wh and 15) + 1, c)
                        }
                    }
                }
                tx += 16
            }
            ty += 16
        }
    }

    private fun inflate(inf: Inflater, size: Int): ByteArray {
        val data = readFully(input.readInt().also { bytesIn += 4 })
        inf.setInput(data)
        val out = ByteArray(size)
        var done = 0
        while (done < size) {
            val k = inf.inflate(out, done, size - done)
            if (k == 0 && (inf.needsInput() || inf.finished())) break
            done += k
        }
        return out
    }

    private fun zlib(x: Int, y: Int, w: Int, h: Int) = rawFrom(inflate(zlibInflater, w * h * 4), 0, x, y, w, h)

    /** ZRLE reads from one continuous zlib stream; the compressed length is per rectangle. */
    private var zbuf = ByteArray(0)
    private var zpos = 0

    private fun zrle(x: Int, y: Int, w: Int, h: Int) {
        val len = input.readInt(); bytesIn += 4
        zrleInflater.setInput(readFully(len))
        val chunks = ArrayList<ByteArray>()
        var total = 0
        while (true) {
            val buf = ByteArray(1 shl 16)
            val k = zrleInflater.inflate(buf)
            if (k > 0) { chunks += if (k == buf.size) buf else buf.copyOf(k); total += k }
            if (k == 0 && (zrleInflater.needsInput() || zrleInflater.finished())) break
        }
        zbuf = ByteArray(total); var o = 0
        for (c in chunks) { System.arraycopy(c, 0, zbuf, o, c.size); o += c.size }
        zpos = 0

        var ty = y
        while (ty < y + h) {
            val th = minOf(64, y + h - ty)
            var tx = x
            while (tx < x + w) {
                val tw = minOf(64, x + w - tx)
                zrleTile(tx, ty, tw, th)
                tx += 64
            }
            ty += 64
        }
    }

    private fun zByte(): Int = zbuf[zpos++].toInt() and 0xff

    /** CPIXEL: 3 bytes (B, G, R) for 32 bpp depth 24. */
    private fun cpixel(): Int {
        val b = zbuf[zpos].toInt() and 0xff; val g = zbuf[zpos + 1].toInt() and 0xff; val r = zbuf[zpos + 2].toInt() and 0xff
        zpos += 3
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun runLength(): Int {
        var len = 1
        while (true) { val b = zByte(); len += b; if (b != 255) return len }
    }

    private fun zrleTile(tx: Int, ty: Int, tw: Int, th: Int) {
        val sub = zByte()
        when {
            sub == 0 -> for (row in 0 until th) for (col in 0 until tw) pixels[(ty + row) * width + tx + col] = cpixel()
            sub == 1 -> fill(tx, ty, tw, th, cpixel())
            sub in 2..16 -> {
                val pal = IntArray(sub) { cpixel() }
                val bits = when { sub == 2 -> 1; sub <= 4 -> 2; else -> 4 }
                for (row in 0 until th) {
                    var bitPos = 8; var cur = 0
                    for (col in 0 until tw) {
                        if (bitPos == 8) { cur = zByte(); bitPos = 0 }
                        val idx = (cur shr (8 - bits - bitPos)) and ((1 shl bits) - 1)
                        bitPos += bits
                        pixels[(ty + row) * width + tx + col] = pal[idx]
                    }
                }
            }
            sub == 128 -> {
                var i = 0; val total = tw * th
                while (i < total) {
                    val c = cpixel(); val n = runLength()
                    repeat(n) { val p = i + it; pixels[(ty + p / tw) * width + tx + p % tw] = c }
                    i += n
                }
            }
            sub >= 130 -> {
                val pal = IntArray(sub - 128) { cpixel() }
                var i = 0; val total = tw * th
                while (i < total) {
                    val v = zByte()
                    val c = pal[v and 127]
                    val n = if (v and 128 != 0) runLength() else 1
                    repeat(n) { val p = i + it; pixels[(ty + p / tw) * width + tx + p % tw] = c }
                    i += n
                }
            }
            else -> throw IllegalStateException("ZRLE SUBENCODING $sub")
        }
    }

    private fun readString(): String {
        val len = input.readInt()
        val b = ByteArray(len); input.readFully(b)
        return String(b)
    }

    companion object {
        /** VNC authentication: DES of the challenge, the key being the password with each byte's bits mirrored. */
        fun vncResponse(password: String, challenge: ByteArray): ByteArray {
            val key = ByteArray(8)
            val pw = password.toByteArray(Charsets.ISO_8859_1)
            for (i in 0 until 8) {
                val c = if (i < pw.size) pw[i].toInt() and 0xff else 0
                var r = 0
                for (bit in 0 until 8) if (c and (1 shl bit) != 0) r = r or (1 shl (7 - bit))
                key[i] = r.toByte()
            }
            @Suppress("GetInstance")
            val des = Cipher.getInstance("DES/ECB/NoPadding")
            des.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"))
            return des.doFinal(challenge)
        }
    }
}
