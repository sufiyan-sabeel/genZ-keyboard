package com.genz.keyboard

/**
 * Emoji suggestions for the word being typed.
 *
 * The suggestion bar already offers corrections and next words. This adds one thing a
 * modern keyboard is expected to do: when you have just typed a word that has an obvious
 * emoji, offer that emoji as a chip you can tap.
 *
 * Design choices:
 * - A small, curated map, not a huge lookup table. Every entry is a word people actually
 *   type, and every emoji is one they would actually send. A 2000-entry table would fire on
 *   ordinary words and make the bar noisy.
 * - Exact match wins; otherwise a longer key may match the start of the word
 *   ("laughing" → 😂). Short keys never prefix-match, so "ok" does not fire on "okay-ish"
 *   words it has no business matching.
 * - Nothing here learns, stores or sends anything. It is a constant map.
 */
internal object EmojiSuggest {

    /** Lowercase trigger → the emoji to offer. Keep entries meaningful, not exhaustive. */
    private val MAP: Map<String, String> = linkedMapOf(
        "lol" to "😂", "lmao" to "🤣", "rofl" to "🤣", "omg" to "😱",
        "wtf" to "😳", "idk" to "🤷", "tbh" to "💬", "fr" to "💯",
        "facts" to "💯", "bruh" to "😑", "sus" to "👀", "slay" to "💅",
        "vibe" to "✨", "vibes" to "✨", "mood" to "😌", "fire" to "🔥",
        "lit" to "🔥", "goat" to "🐐", "flex" to "💪", "bet" to "🤝",
        "yolo" to "🙌", "fomo" to "😬", "gg" to "🎮", "cap" to "🧢",
        "periodt" to "💅", "sheesh" to "😤", "oof" to "😬", "yikes" to "😬",
        "nah" to "🙅", "yep" to "👍", "happy" to "😊", "sad" to "😢",
        "cry" to "😭", "crying" to "😭", "laugh" to "😂", "laughing" to "😂",
        "love" to "❤️", "loved" to "❤️", "hate" to "😤", "angry" to "😠",
        "tired" to "😴", "sleepy" to "😴", "excited" to "🤩", "nervous" to "😬",
        "scared" to "😨", "confused" to "🤔", "thinking" to "🤔", "proud" to "🥹",
        "grateful" to "🙏", "sorry" to "😔", "blessed" to "🙏", "hi" to "👋",
        "hello" to "👋", "hey" to "👋", "bye" to "👋", "goodbye" to "👋",
        "thanks" to "🙏", "thank" to "🙏", "please" to "🙏", "congrats" to "🎉",
        "congratulations" to "🎉", "welcome" to "🤗", "yes" to "✅", "no" to "❌",
        "ok" to "👌", "okay" to "👌", "sure" to "👍", "cool" to "😎",
        "coffee" to "☕", "tea" to "🍵", "pizza" to "🍕", "burger" to "🍔",
        "food" to "🍽️", "hungry" to "🍽️", "breakfast" to "🥞", "lunch" to "🍱",
        "dinner" to "🍽️", "cake" to "🎂", "birthday" to "🎂", "party" to "🎉",
        "beer" to "🍺", "water" to "💧", "sleep" to "😴", "goodnight" to "🌙",
        "morning" to "☀️", "goodmorning" to "☀️", "night" to "🌙", "sunny" to "☀️",
        "rain" to "🌧️", "rainy" to "🌧️", "cold" to "🥶", "hot" to "🥵",
        "work" to "💼", "office" to "🏢", "meeting" to "📅", "deadline" to "⏰",
        "exam" to "📝", "study" to "📚", "studying" to "📚", "homework" to "📝",
        "class" to "🏫", "college" to "🎓", "school" to "🏫", "graduation" to "🎓",
        "code" to "💻", "coding" to "💻", "bug" to "🐛", "deploy" to "🚀",
        "shipped" to "🚀", "launch" to "🚀", "ship" to "🚀", "money" to "💰",
        "broke" to "💸", "paid" to "💸", "gym" to "🏋️", "workout" to "🏋️",
        "run" to "🏃", "running" to "🏃", "football" to "⚽", "cricket" to "🏏",
        "match" to "🏏", "game" to "🎮", "gaming" to "🎮", "movie" to "🎬",
        "music" to "🎵", "song" to "🎵", "singing" to "🎤", "dance" to "💃",
        "travel" to "✈️", "trip" to "✈️", "home" to "🏠", "train" to "🚆",
        "car" to "🚗", "phone" to "📱", "photo" to "📸", "selfie" to "🤳",
        "time" to "⏰", "today" to "📅", "tomorrow" to "📅", "weekend" to "🎊",
        "star" to "⭐", "heart" to "❤️", "wow" to "🤩", "nice" to "👍",
        "best" to "🏆", "win" to "🏆", "won" to "🏆", "lost" to "😞",
        "help" to "🆘", "wait" to "⏳", "later" to "⏳", "done" to "✅",
        "finished" to "✅", "start" to "▶️", "stop" to "⏹️", "idea" to "💡",
        "smart" to "💡", "genius" to "🧠", "brain" to "🧠", "strong" to "💪",
        "power" to "⚡", "energy" to "⚡", "lucky" to "🍀", "luck" to "🍀",
        "peace" to "✌️", "smile" to "😄", "kiss" to "😘", "hug" to "🤗",
        "friend" to "🫂", "friends" to "🫂"
    )

    /** Keys shorter than this are only matched exactly, so "ok" cannot swallow "okra". */
    private const val PREFIX_MIN_LENGTH = 4

    /**
     * The emoji to offer for [word], or null when there is nothing worth offering.
     * Matching is case-insensitive and ignores surrounding punctuation.
     */
    fun forWord(word: String): String? {
        val w = word.trim { !it.isLetter() && it != '-' }.lowercase()
        if (w.length < 2) return null
        MAP[w]?.let { return it }
        if (w.length >= PREFIX_MIN_LENGTH) {
            // longest matching key wins, so "workouts" gives 🏋️ and not the 💼 of "work"
            var bestKey = ""
            var bestEmoji: String? = null
            for ((key, emoji) in MAP) {
                if (key.length > bestKey.length && key.length >= PREFIX_MIN_LENGTH && w.startsWith(key)) {
                    bestKey = key
                    bestEmoji = emoji
                }
            }
            return bestEmoji
        }
        return null
    }
}
