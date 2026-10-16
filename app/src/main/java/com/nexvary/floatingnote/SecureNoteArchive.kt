package com.fgmachines.fgnote

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted, authenticated backup.
 * Format NFN1 | random 16-byte salt | random 12-byte nonce | AES-GCM ciphertext/tag.
 * PBKDF2-HMAC-SHA256 / 210,000 rounds / AES-256-GCM / 128-bit tag.
 * Never silently fall back to unencrypted backups.
 */
object SecureNoteArchive {
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'F'.code.toByte(),
        'N'.code.toByte(), '1'.code.toByte())
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val HEADER = 4 + SALT_SIZE + NONCE_SIZE
    private const val ITERATIONS = 210_000
    private const val MAX_ENCRYPTED = NoteArchive.MAX_BYTES + HEADER + 16

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try {
                return SecretKeySpec(raw, "AES")
            } finally {
                raw.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(data: ByteArray, password: CharArray): ByteArray {
        require(password.size >= 10) { "Password must have at least 10 characters" }
        require(data.size <= NoteArchive.MAX_BYTES) { "Archive too large" }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val header = MAGIC + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(data)
    }

    fun decrypt(encrypted: ByteArray, password: CharArray): ByteArray {
        require(encrypted.size in (HEADER + 16)..MAX_ENCRYPTED) { "Invalid encrypted backup" }
        val header = encrypted.copyOfRange(0, HEADER)
        require(header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not a NEXVARY encrypted backup"
        }
        val salt = header.copyOfRange(4, 4 + SALT_SIZE)
        val nonce = header.copyOfRange(4 + SALT_SIZE, HEADER)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return cipher.doFinal(encrypted, HEADER, encrypted.size - HEADER)
        } catch (error: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("Wrong password or damaged backup", error)
        }
    }

    fun export(notes: List<Note>, password: CharArray, output: OutputStream) {
        val plain = ByteArrayOutputStream()
        NoteArchive.export(notes, plain)
        val bytes = plain.toByteArray()
        try {
            output.write(encrypt(bytes, password))
            output.flush()
        } finally {
            bytes.fill(0)
        }
    }

    fun read(input: InputStream, password: CharArray): List<Note> {
        val buffer = ByteArray(8192)
        val encoded = ByteArrayOutputStream()
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(encoded.size() + count <= MAX_ENCRYPTED) { "Encrypted backup too large" }
            encoded.write(buffer, 0, count)
        }
        val plain = decrypt(encoded.toByteArray(), password)
        return try {
            NoteArchive.read(ByteArrayInputStream(plain))
        } finally {
            plain.fill(0)
        }
    }
}
