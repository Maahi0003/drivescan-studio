package com.drivescan.kmp.scan

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Password-protected PDF export (v1.1.0).
 *
 * Writes a minimal image PDF (one JPEG page image per page, embedded with
 * DCTDecode — no re-encode) protected with the PDF Standard Security
 * Handler: V=2, R=3, 128-bit RC4, permissions P=-4 (all allowed once the
 * file is opened). Implemented from ISO 32000 so it needs no libraries;
 * the owner password is set equal to the user password for simplicity.
 * The app's stored originals are never touched — this only writes the
 * encrypted copy handed to the share flow.
 */
object PdfEncrypt {

    data class Page(val jpeg: ByteArray, val width: Int, val height: Int)

    private val PADDING = byteArrayOf(
        0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41,
        0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
        0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(),
        0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A
    )

    private const val KEY_LEN = 16 // 128-bit
    private const val REVISION = 3
    private const val PERMISSIONS = -4

    fun writeEncryptedPdf(pages: List<Page>, password: String, out: File): Boolean {
        if (pages.isEmpty() || password.isEmpty()) return false
        return try {
            val bytes = build(pages, password)
            FileOutputStream(out).use { it.write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun build(pages: List<Page>, password: String): ByteArray {
        val fileId = MessageDigest.getInstance("MD5")
            .digest("drivescan-${System.nanoTime()}-$password".toByteArray(Charsets.UTF_8))

        // --- Standard Security Handler key schedule (R=3) ---
        val userPad = padPassword(password)
        val ownerValue = computeOwnerValue(userPad, userPad) // owner pw == user pw
        val fileKey = computeFileKey(userPad, ownerValue, fileId)
        val userValue = computeUserValue(fileKey, fileId)

        val pageObjBase = 3
        val encryptObj = pageObjBase + pages.size * 3
        val totalObjects = encryptObj // objects are 1..encryptObj

        val baos = ByteArrayOutputStream()
        val offsets = IntArray(totalObjects + 1)
        fun writeAscii(s: String) = baos.write(s.toByteArray(Charsets.US_ASCII))
        fun beginObj(num: Int) {
            offsets[num] = baos.size()
            writeAscii("$num 0 obj\n")
        }
        fun encryptFor(objNum: Int, data: ByteArray): ByteArray {
            val objectKey = objectKey(fileKey, objNum, 0)
            return rc4(objectKey, data)
        }

        writeAscii("%PDF-1.4\n")
        // Binary-marker comment so tools treat the file as binary.
        baos.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        // 1: Catalog
        beginObj(1)
        writeAscii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // 2: Pages
        beginObj(2)
        val kids = pages.indices.joinToString(" ") { "${pageObjBase + it * 3} 0 R" }
        writeAscii("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")

        // Per page: Page, Contents, Image XObject
        pages.forEachIndexed { i, page ->
            val pageNum = pageObjBase + i * 3
            val contentNum = pageNum + 1
            val imageNum = pageNum + 2
            beginObj(pageNum)
            writeAscii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${page.width} ${page.height}] " +
                    "/Resources << /XObject << /Im0 $imageNum 0 R >> >> " +
                    "/Contents $contentNum 0 R >>\nendobj\n"
            )
            val content = "q\n${page.width} 0 0 ${page.height} 0 0 cm\n/Im0 Do\nQ\n"
                .toByteArray(Charsets.US_ASCII)
            val encContent = encryptFor(contentNum, content)
            beginObj(contentNum)
            writeAscii("<< /Length ${encContent.size} >>\nstream\n")
            baos.write(encContent)
            writeAscii("\nendstream\nendobj\n")

            val encImage = encryptFor(imageNum, page.jpeg)
            beginObj(imageNum)
            writeAscii(
                "<< /Type /XObject /Subtype /Image /Width ${page.width} /Height ${page.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                    "/Length ${encImage.size} >>\nstream\n"
            )
            baos.write(encImage)
            writeAscii("\nendstream\nendobj\n")
        }

        // Encrypt dictionary (values themselves are never encrypted)
        beginObj(encryptObj)
        writeAscii(
            "<< /Filter /Standard /V 2 /R $REVISION /Length 128 /P $PERMISSIONS " +
                "/O <${ownerValue.toHex()}> /U <${userValue.toHex()}> >>\nendobj\n"
        )

        // xref + trailer
        val xrefPos = baos.size()
        writeAscii("xref\n0 ${totalObjects + 1}\n")
        writeAscii("0000000000 65535 f \n")
        for (num in 1..totalObjects) {
            writeAscii(String.format("%010d 00000 n \n", offsets[num]))
        }
        val idHex = fileId.toHex()
        writeAscii(
            "trailer\n<< /Size ${totalObjects + 1} /Root 1 0 R " +
                "/Encrypt $encryptObj 0 R /ID [<$idHex><$idHex>] >>\n" +
                "startxref\n$xrefPos\n%%EOF\n"
        )
        return baos.toByteArray()
    }

    // ---- security handler primitives ---------------------------------------

    private fun padPassword(password: String): ByteArray {
        val pwBytes = password.toByteArray(Charsets.ISO_8859_1)
        val out = ByteArray(32)
        for (i in 0 until 32) {
            out[i] = if (i < minOf(pwBytes.size, 32)) pwBytes[i] else PADDING[i - minOf(pwBytes.size, 32)]
        }
        return out
    }

    private fun computeOwnerValue(ownerPad: ByteArray, userPad: ByteArray): ByteArray {
        var digest = md5(ownerPad)
        repeat(50) { digest = md5(digest) }
        val key = digest.copyOf(KEY_LEN)
        var data = rc4(key, userPad)
        for (i in 1..19) {
            val iterKey = ByteArray(KEY_LEN) { (key[it].toInt() xor i).toByte() }
            data = rc4(iterKey, data)
        }
        return data
    }

    private fun computeFileKey(userPad: ByteArray, ownerValue: ByteArray, fileId: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(userPad)
        md.update(ownerValue)
        md.update(
            byteArrayOf(
                (PERMISSIONS and 0xFF).toByte(),
                ((PERMISSIONS shr 8) and 0xFF).toByte(),
                ((PERMISSIONS shr 16) and 0xFF).toByte(),
                ((PERMISSIONS shr 24) and 0xFF).toByte()
            )
        )
        md.update(fileId)
        var digest = md.digest()
        repeat(50) { digest = md5(digest.copyOf(KEY_LEN)) }
        return digest.copyOf(KEY_LEN)
    }

    private fun computeUserValue(fileKey: ByteArray, fileId: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(PADDING)
        md.update(fileId)
        var data = rc4(fileKey, md.digest())
        for (i in 1..19) {
            val iterKey = ByteArray(KEY_LEN) { (fileKey[it].toInt() xor i).toByte() }
            data = rc4(iterKey, data)
        }
        // 16 bytes of arbitrary padding complete the 32-byte /U value.
        return data + ByteArray(16) { (it * 37 + 11).toByte() }
    }

    private fun objectKey(fileKey: ByteArray, objNum: Int, genNum: Int): ByteArray {
        val md = MessageDigest.getInstance("MD5")
        md.update(fileKey)
        md.update(
            byteArrayOf(
                (objNum and 0xFF).toByte(),
                ((objNum shr 8) and 0xFF).toByte(),
                ((objNum shr 16) and 0xFF).toByte(),
                (genNum and 0xFF).toByte(),
                ((genNum shr 8) and 0xFF).toByte()
            )
        )
        val digest = md.digest()
        return digest.copyOf(minOf(KEY_LEN + 5, digest.size))
    }

    private fun md5(data: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(data)

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val tmp = s[i]; s[i] = s[j]; s[j] = tmp
        }
        val out = ByteArray(data.size)
        var a = 0
        var b = 0
        for (n in data.indices) {
            a = (a + 1) and 0xFF
            b = (b + s[a]) and 0xFF
            val tmp = s[a]; s[a] = s[b]; s[b] = tmp
            val k = s[(s[a] + s[b]) and 0xFF]
            out[n] = (data[n].toInt() xor k).toByte()
        }
        return out
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) sb.append(String.format("%02x", b.toInt() and 0xFF))
        return sb.toString()
    }
}
