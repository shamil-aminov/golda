package sh.aminov.golda.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import sh.aminov.golda.domain.VoiceItem
import sh.aminov.golda.domain.VoicePrompt
import sh.aminov.golda.domain.VoiceResult
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import sh.aminov.golda.domain.tr

/**
 * Records one voice note: Opus in OGG from Android 10, where MediaRecorder has it; AAC in an ADTS
 * stream (.aac, what Gemini takes as audio/aac) on Android 8–9.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var peak = 0f
    private var samples = 0

    fun start(): File {
        val opus = Build.VERSION.SDK_INT >= 29
        val target = File(VoiceQueue.dir(context), "${System.currentTimeMillis()}." + if (opus) "ogg" else "aac")
        val created = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        recorder = created.apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            if (opus) {
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioEncodingBitRate(24_000)
            } else {
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32_000)
            }
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setMaxDuration(60_000)
            setOutputFile(target)
            prepare()
            start()
        }
        file = target
        startedAt = System.currentTimeMillis()
        peak = 0f
        samples = 0
        return target
    }

    /**
     * The finished file, or null when nothing usable was recorded: an empty file, a tap shorter
     * than 0.8 s, or silence (judged only when the level was actually being watched).
     */
    fun stop(): File? {
        val r = recorder ?: return null
        val accidental = System.currentTimeMillis() - startedAt < 800 || (samples >= 3 && peak < 0.03f)
        recorder = null
        runCatching { r.stop() } // throws when nothing was recorded or the 60 s limit already stopped it
        r.release()
        val f = file ?: return null
        if (accidental || f.length() == 0L) {
            f.delete()
            return null
        }
        return f
    }

    fun cancel() {
        stop()?.delete()
    }

    /** How loud it has been since the last call, 0..1; 0 when not recording. Also keeps the loudest moment. */
    fun level(): Float {
        val now = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)
        samples++
        peak = maxOf(peak, now)
        return now
    }
}

/**
 * Voice notes not booked yet. A note's file name is the time it was recorded, plus "-N" once N attempts
 * at it have failed. The queue is tried on every start; notes that cannot be understood are set aside
 * in "aside/" and are tried again only when the user asks (Settings → Голос), never on their own.
 */
object VoiceQueue {
    /** Attempts at a note that Gemini keeps failing (busy, over quota) before it is set aside. */
    const val MAX_ATTEMPTS = 3

    fun dir(context: Context) = File(context.filesDir, "voice").apply { mkdirs() }

    private fun asideDir(context: Context) = File(dir(context), "aside").apply { mkdirs() }

    /** Waiting their turn, oldest first. */
    fun pending(context: Context): List<File> = notes(dir(context))

    /** Set aside after a failure, oldest first. */
    fun setAside(context: Context): List<File> = notes(asideDir(context))

    /** Everything not booked yet, except a note still being recorded. */
    fun count(context: Context): Int = (pending(context) + setAside(context)).count { !fresh(it) }

    private fun notes(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.extension in AUDIO }.orEmpty().sortedBy { recordedAt(it) }

    /** What a note can be: Opus (10+), AAC (8–9), and WAV for tests. */
    private val AUDIO = setOf("ogg", "aac", "m4a", "wav")

    fun recordedAt(file: File): Long = file.nameWithoutExtension.substringBefore('-').toLongOrNull() ?: file.lastModified()

    fun attempts(file: File): Int = file.nameWithoutExtension.substringAfter('-', "").toIntOrNull() ?: 0

    /** Written to in the last moments: a recording still going. */
    fun fresh(file: File): Boolean = System.currentTimeMillis() - file.lastModified() < 2_000

    /** One more failed attempt, remembered in the name. */
    fun countAttempt(file: File): File {
        val target = File(file.parentFile, "${recordedAt(file)}-${attempts(file) + 1}.${file.extension}")
        return if (file.renameTo(target)) target else file
    }

    fun putAside(context: Context, file: File) {
        val target = File(asideDir(context), "${recordedAt(file)}.${file.extension}")
        if (!file.renameTo(target)) file.delete()
    }

    /** "Повторить": the notes set aside go back in the queue, and every note starts its attempts over. */
    fun requeue(context: Context) {
        for (file in setAside(context) + pending(context).filter { attempts(it) > 0 }) {
            file.renameTo(File(dir(context), "${recordedAt(file)}.${file.extension}"))
        }
    }

    /** "Удалить": every note not booked yet, except one being recorded right now. */
    fun clear(context: Context) {
        (pending(context) + setAside(context)).filterNot(::fresh).forEach { it.delete() }
    }

    /** "Стереть всё": every note, whatever its state. */
    fun wipe(context: Context) {
        dir(context).deleteRecursively()
    }
}

/**
 * Why a note was not understood. [Kind.OFFLINE]: no network; try again later, as often as it takes.
 * [Kind.BUSY]: Gemini is overloaded or over quota; try again later, a few times. [Kind.REJECTED]: a
 * refused request or a reply that cannot be read; trying again would give the same, so the note is
 * set aside. [Kind.NO_KEY]: there is no key yet.
 */
class GeminiException(message: String, val kind: Kind) : Exception(message) {
    enum class Kind { OFFLINE, BUSY, REJECTED, NO_KEY }
}

/** What turns a note into items: Gemini with the saved key. The instrumented tests swap in their own. */
fun interface VoiceParser {
    /** Throws only [GeminiException]. */
    suspend fun parse(audio: File, system: String, model: String): VoiceResult
}

class GeminiParser(private val settings: SettingsStore) : VoiceParser {
    override suspend fun parse(audio: File, system: String, model: String): VoiceResult {
        val key = settings.geminiKey() ?: throw GeminiException(tr("нет ключа", "no key"), GeminiException.Kind.NO_KEY)
        return Gemini.parse(audio, system, key, model)
    }
}

object Gemini {
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"

    suspend fun parse(audio: File, system: String, key: String, model: String): VoiceResult = withContext(Dispatchers.IO) {
        val mime = when (audio.extension) {
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            else -> "audio/ogg"
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(
                            JSONObject().put(
                                "inlineData",
                                JSONObject().put("mimeType", mime).put("data", Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP)),
                            ),
                        ),
                    ),
                ),
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", JSONObject(VoicePrompt.schema)),
            )

        val url = URI("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").toURL()
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(error).getJSONObject("error").getString("message") }.getOrDefault("HTTP $code")
                val busy = code == 429 || code >= 500
                throw GeminiException(message, if (busy) GeminiException.Kind.BUSY else GeminiException.Kind.REJECTED)
            }
            readReply(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException(e.message ?: tr("нет сети", "no network"), GeminiException.Kind.OFFLINE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Whatever else goes wrong here is about this note, not the network: never a crash.
            throw GeminiException(e.message ?: e.javaClass.simpleName, GeminiException.Kind.REJECTED)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The items of a generateContent reply. A blocked prompt, a reply cut short, an array where an
     * object belongs, anything not in the expected shape gives [GeminiException] of
     * [GeminiException.Kind.REJECTED], never another exception.
     */
    fun readReply(body: String): VoiceResult {
        val unreadable = tr("ответ не разобрать", "the reply could not be read")
        try {
            val reply = JSONObject(body)
            val candidate = reply.optJSONArray("candidates")?.optJSONObject(0)
            if (candidate == null) {
                val blocked = reply.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                val message = if (blocked.isNotBlank()) tr("запрос отклонён ($blocked)", "request blocked ($blocked)") else unreadable
                throw GeminiException(message, GeminiException.Kind.REJECTED)
            }
            val text = candidate.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            if (text.isBlank()) {
                val reason = candidate.optString("finishReason").takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                throw GeminiException(unreadable + reason, GeminiException.Kind.REJECTED)
            }
            return read(JSONObject(text))
        } catch (e: GeminiException) {
            throw e
        } catch (e: Exception) {
            throw GeminiException(unreadable, GeminiException.Kind.REJECTED)
        }
    }

    private fun read(json: JSONObject): VoiceResult {
        fun JSONObject.str(name: String) = if (isNull(name) || !has(name)) null else getString(name).takeIf { it.isNotBlank() }
        val items = json.optJSONArray("items") ?: JSONArray()
        return VoiceResult(
            transcript = json.optString("transcript"),
            items = (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                VoiceItem(
                    intent = o.optString("intent", "unknown"),
                    amount = o.str("amount"),
                    currency = o.str("currency"),
                    note = o.optString("note"),
                    category = o.str("category"),
                    accountId = o.str("account_id"),
                    toAccountId = o.str("to_account_id"),
                    toAmount = o.str("to_amount"),
                    date = o.str("date"),
                )
            },
        )
    }
}

/** Encrypts small secrets with a key that never leaves the phone's keystore. */
object KeyVault {
    private const val ALIAS = "golda-secrets"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    /** Null when the data was encrypted on another phone (restored from a backup) or is damaged. */
    fun decrypt(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        }
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}
