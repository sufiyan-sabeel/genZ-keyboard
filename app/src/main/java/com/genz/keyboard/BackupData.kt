package com.genz.keyboard

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Collects the user's data for a backup, and puts it back on restore.
 *
 * Kept separate from [BackupService] so the service stays free of Android types and can be tested
 * with plain files. Everything here is the Context-aware half.
 */
object BackupData {

    private const val DATA_PREFS = "expressive_keyboard_v8_data"
    private const val KEY_WORDS = "user_words"
    private const val KEY_BIGRAMS = "user_bigrams"

    fun themes(context: Context): List<ThemeDoc> = ThemeStore(ThemeAssets.themeDir(context)).list()

    fun imageBytes(context: Context, name: String): ByteArray? =
        runCatching { ThemeAssets.wallpaperFile(context, name)?.takeIf { it.isFile }?.readBytes() }.getOrNull()

    fun settingsJson(context: Context): String = KeyboardPrefs.exportJson(context)

    /** Learned words and bigrams, read from the vocabulary prefs file. */
    fun words(context: Context): Pair<Map<String, Int>, Map<String, Int>> {
        val p = context.getSharedPreferences(DATA_PREFS, Context.MODE_PRIVATE)
        return readCounts(p.getString(KEY_WORDS, null)) to readCounts(p.getString(KEY_BIGRAMS, null))
    }

    /**
     * Merge restored vocabulary into the live one.
     *
     * Merged rather than replaced: a backup taken last month must not erase words learned since.
     * The engine reloads afterwards so the restored words are immediately usable for predictions.
     */
    fun applyWords(context: Context, words: Map<String, Int>, bigrams: Map<String, Int>) {
        val p = context.getSharedPreferences(DATA_PREFS, Context.MODE_PRIVATE)
        val mergedWords = BackupBundle.mergeWords(readCounts(p.getString(KEY_WORDS, null)), words)
        val mergedBigrams = BackupBundle.mergeWords(readCounts(p.getString(KEY_BIGRAMS, null)), bigrams)
        // commit(), not apply(): the restore reports success only after the data is actually on disk,
        // and a process death immediately afterwards must not lose the restored vocabulary
        val written = p.edit()
            .putString(KEY_WORDS, encodeCounts(mergedWords))
            .putString(KEY_BIGRAMS, encodeCounts(mergedBigrams))
            .commit()
        if (!written) error("could not persist restored vocabulary")
        // reload so restored words are usable for predictions without waiting for a restart
        WordPredictorEngine.loadPersonalDictionary(context)
    }

    private fun readCounts(raw: String?): Map<String, Int> {
        val o = raw?.let { ThemeJson.decode(it) } ?: return emptyMap()
        return o.entries.mapNotNull { (k, v) ->
            (v as? Number)?.toInt()?.let { k to it }
        }.toMap()
    }

    private fun encodeCounts(m: Map<String, Int>): String =
        m.entries.joinToString(",", "{", "}") { "\"${it.key.replace("\"", "\\\"")}\":${it.value}" }
}

/**
 * Guards against two backup operations running at once.
 *
 * Export and restore both write to the themes directory; running them together would let a restore
 * read half-written files. The guard is a single atomic flag, so a second attempt is refused rather
 * than queued — the user sees "an operation is already running" instead of a corrupt result.
 */
object BackupOperation {

    private val busy = AtomicBoolean(false)

    val isBusy: Boolean get() = busy.get()

    /** Run [block] if nothing else is running; null when the guard refused. */
    fun <T> run(block: () -> T): T? {
        if (!busy.compareAndSet(false, true)) return null
        return try {
            block()
        } finally {
            busy.set(false)
        }
    }

    /** Test hook: clear a guard left set by a crash. */
    internal fun reset() { busy.set(false) }
}
