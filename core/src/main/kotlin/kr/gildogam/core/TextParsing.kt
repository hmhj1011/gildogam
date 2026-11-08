package kr.gildogam.core

/** What a passenger meant when they spoke into the "사연" button. */
enum class Intent { STORY, SONG, QUIZ }

/**
 * Small, rule-based Korean understanding for speech-to-text results.
 * It is deliberately simple and predictable so it works on-device without a network.
 */
object TextParsing {

    private val punctuation = Regex("[.,!?~…·\"'“”‘’()\\[\\]]")
    private val spaces = Regex("\\s+")

    fun normalize(text: String): String = text.replace(punctuation, " ").replace(spaces, " ").trim()

    // ---------- names ----------

    private val nameSplitters = Regex("\\s+|,|그리고|하고|이랑|랑")

    private val stopWords = setOf(
        "저는", "저", "제", "나는", "나", "내", "우리", "우리는", "이름은", "이름", "제이름은", "내이름은",
        "안녕", "안녕하세요", "반가워요", "반갑습니다", "그리고", "또", "여기", "있어요", "있고", "있어",
        "입니다", "이에요", "예요", "에요", "요", "네", "응", "음", "어", "그", "이", "는", "은", "님", "도",
        "다", "같이", "함께", "탔어요", "타고", "있습니다", "왔어요", "가족", "청취자", "모두", "다들",
    )

    // Longest first so "이에요" is removed before "요".
    private val nameSuffixes = listOf(
        "입니다", "이에요", "이고요", "이고", "예요", "에요", "이요", "님이요", "님", "이랑", "하고", "랑", "요",
    )

    /** "엄마 아빠 지우 그리고 할머니요" → [엄마, 아빠, 지우, 할머니] */
    fun parseNames(text: String, max: Int = 6): List<String> {
        val tokens = normalize(text).split(nameSplitters).map { it.trim() }.filter { it.isNotEmpty() }
        val result = LinkedHashSet<String>()
        for (raw in tokens) {
            if (raw in stopWords) continue
            var token = raw
            for (suffix in nameSuffixes) {
                if (token.length > suffix.length && token.endsWith(suffix)) {
                    token = token.dropLast(suffix.length)
                    break
                }
            }
            // Topic/subject particles only when the rest still looks like a name ("지우는" → "지우").
            if (token.length >= 3 && (token.endsWith("는") || token.endsWith("은") || token.endsWith("가"))) {
                token = token.dropLast(1)
            }
            if (token in stopWords) continue
            if (token.length !in 1..5) continue
            if (!token.all { it.code in 0xAC00..0xD7A3 || it.isLetter() }) continue
            result += token
            if (result.size >= max) break
        }
        return result.toList()
    }

    /** Who is talking, if they said so: "나 지우인데", "지우예요", "저 민수입니다". */
    fun findSpeaker(text: String, listeners: List<String>): String? {
        val t = normalize(text).replace(" ", "")
        for (name in listeners) {
            val patterns = listOf(
                "나$name", "저$name", "${name}인데", "${name}이에요", "${name}예요", "${name}입니다", "${name}이야", "${name}야",
            )
            if (patterns.any { t.contains(it) }) return name
            if (t.startsWith(name) && t.length > name.length) {
                val next = t[name.length]
                if (next == '가' || next == '이' || next == '는' || next == '은' || next == '야') return name
            }
        }
        return null
    }

    /** Removes a leading self-introduction: "엄마인데 아이유 좋은 날" → "아이유 좋은 날". */
    fun stripSpeakerIntro(text: String, listeners: List<String>): String {
        var t = normalize(text)
        for (name in listeners) {
            val intros = listOf(
                "나 ${name}인데", "저 ${name}인데", "나 ${name}야", "저 ${name}예요", "저 ${name}이에요",
                "${name}인데", "${name}이에요", "${name}예요", "${name}입니다", "${name}이야", "${name}야",
            )
            for (intro in intros) {
                if (t.startsWith(intro)) {
                    t = t.removePrefix(intro).trim()
                    return t
                }
            }
        }
        return t
    }

    // ---------- intent ----------

    private val songWords = listOf("노래", "틀어", "신청곡", "들려줘", "들려 줘", "들려주세요", "플레이")

    fun classify(text: String): Intent {
        val t = normalize(text)
        if (t.contains("퀴즈")) return Intent.QUIZ
        if (songWords.any { t.contains(it) }) return Intent.SONG
        return Intent.STORY
    }

    // ---------- songs ----------

    private val songNoise = listOf(
        "신청곡은", "신청곡으로", "신청곡", "신청할게요", "신청합니다", "신청해요", "신청해",
        "틀어주세요", "틀어 주세요", "틀어줄래", "틀어줘요", "틀어 줘", "틀어줘", "틀어",
        "들려주세요", "들려 주세요", "들려줘요", "들려줘", "플레이해줘", "플레이",
        "부탁해요", "부탁해", "부탁드려요", "노래로", "노래를", "노래", "좀", "주세요", "줘",
    )

    /** "아이유 좋은 날 틀어줘" → "아이유 좋은 날" */
    fun songQuery(text: String): String {
        var t = " " + normalize(text) + " "
        for (noise in songNoise) t = t.replace(noise, " ")
        return t.replace(spaces, " ").trim()
    }

    // ---------- quiz ----------

    private val ordinalWords = listOf(
        listOf("1번", "일번", "첫번째", "첫째", "하나", "1"),
        listOf("2번", "이번", "두번째", "둘째", "둘", "2"),
        listOf("3번", "삼번", "세번째", "셋째", "셋", "3"),
        listOf("4번", "사번", "네번째", "넷째", "넷", "4"),
    )

    /** Index of the option the speaker chose, or -1. Option text beats ordinal words. */
    fun matchOption(text: String, options: List<String>): Int {
        val t = normalize(text).replace(" ", "")
        if (t.isEmpty()) return -1
        options.forEachIndexed { i, option ->
            val o = normalize(option).replace(" ", "")
            if (o.isNotEmpty() && t.contains(o)) return i
        }
        options.forEachIndexed { i, option ->
            val o = normalize(option).replace(" ", "")
            if (t.length >= 2 && o.contains(t)) return i
        }
        ordinalWords.forEachIndexed { i, words ->
            if (i < options.size && words.any { t.contains(it) }) return i
        }
        return -1
    }
}
