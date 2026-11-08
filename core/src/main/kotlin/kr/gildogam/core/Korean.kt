package kr.gildogam.core

/**
 * Korean particle (조사) helpers so generated lines read naturally:
 * "강릉을", "서울을", "부산을" vs "대구를".
 */
object Korean {
    private const val HANGUL_FIRST = 0xAC00
    private const val HANGUL_LAST = 0xD7A3

    /** Digits read in Korean: 0 영, 1 일, 2 이, 3 삼, 4 사, 5 오, 6 육, 7 칠, 8 팔, 9 구. */
    private val digitHasBatchim = booleanArrayOf(true, true, false, true, false, false, true, true, true, false)

    /** null = unknown (e.g. Latin letters). */
    fun hasBatchim(word: String): Boolean? {
        val last = word.trimEnd().lastOrNull { !it.isWhitespace() && it != ')' && it != '"' && it != '\'' } ?: return null
        val code = last.code
        if (code in HANGUL_FIRST..HANGUL_LAST) return (code - HANGUL_FIRST) % 28 != 0
        if (last in '0'..'9') return digitHasBatchim[last - '0']
        return null
    }

    /** True when the final consonant is ㄹ (for 으로/로). */
    private fun endsWithRieul(word: String): Boolean {
        val last = word.trimEnd().lastOrNull() ?: return false
        val code = last.code
        if (code in HANGUL_FIRST..HANGUL_LAST) return (code - HANGUL_FIRST) % 28 == 8
        return last == '1' || last == '7' || last == '8'
    }

    /** Appends the ending that matches the final consonant, e.g. pick("강릉", "이네요", "네요"). */
    fun pick(word: String, withBatchim: String, without: String): String =
        when (hasBatchim(word)) {
            true -> word + withBatchim
            false -> word + without
            null -> "$word$withBatchim($without)"
        }

    fun eulReul(word: String) = pick(word, "을", "를")
    fun iGa(word: String) = pick(word, "이", "가")
    fun eunNeun(word: String) = pick(word, "은", "는")
    fun gwaWa(word: String) = pick(word, "과", "와")
    fun iRang(word: String) = pick(word, "이랑", "랑")

    /** "이에요"/"예요" */
    fun ieyo(word: String) = pick(word, "이에요", "예요")

    fun euroRo(word: String): String = when (hasBatchim(word)) {
        null -> "$word(으)로"
        true -> if (endsWithRieul(word)) word + "로" else word + "으로"
        false -> word + "로"
    }

    /** "지우님, 민수님, 엄마님" style list with 님. Family roles get no 님 duplication issues. */
    fun joinListeners(names: List<String>): String = names.joinToString(", ") { honorific(it) }

    /** 엄마 → 어머님, 아빠 → 아버님, others → 이름님. */
    fun honorific(name: String): String = when (name) {
        "엄마", "어머니" -> "어머님"
        "아빠", "아버지" -> "아버님"
        "할머니" -> "할머님"
        "할아버지" -> "할아버님"
        else -> if (name.endsWith("님")) name else name + "님"
    }
}
