package xyz.mpv.rex.ui.preferences

import kotlin.math.abs

/**
 * High-performance fuzzy search engine for settings and preference lookups.
 * Supports:
 * 1. Exact matches and prefix matches.
 * 2. Word-boundary matches and word-prefix matches.
 * 3. Substring matching with position weighting.
 * 4. Subsequence matching (e.g. acronyms like "hwdec" -> "hardware decoding").
 * 5. Typo tolerance via bounded Levenshtein distance (e.g. "subtile" -> "subtitles").
 * 6. Multi-word query token matching.
 */
object FuzzySearch {

    /**
     * Scores how well [query] matches [target].
     * Returns a score > 0 if there is a match, or -1 if no match.
     * Higher score indicates higher relevance.
     */
    fun score(query: String, target: String): Int {
        if (query.isBlank() || target.isBlank()) return -1

        val q = query.trim().lowercase()
        val t = target.trim().lowercase()

        // 1. Exact match
        if (q == t) return 1000

        // 2. Target starts with query
        if (t.startsWith(q)) {
            return 850 + (100 - t.length.coerceAtMost(100))
        }

        // 3. Word boundary matches
        val words = t.split(" ", "-", "_", "/", ".", ",", "(", ")")
            .filter { it.isNotEmpty() }

        // Any word exactly equals query
        if (words.any { it == q }) {
            return 750
        }

        // Any word starts with query
        val prefixWord = words.find { it.startsWith(q) }
        if (prefixWord != null) {
            return 650 + (50 - prefixWord.length.coerceAtMost(50))
        }

        // 4. Substring match
        val subIndex = t.indexOf(q)
        if (subIndex >= 0) {
            // Earlier in the string scores higher
            return 500 - subIndex.coerceAtMost(50)
        }

        // 5. Multi-token query check: e.g. "dark amoled" or "hw dec"
        val qTokens = q.split(" ").filter { it.isNotEmpty() }
        if (qTokens.size > 1) {
            var allMatch = true
            var scoreSum = 0
            for (token in qTokens) {
                val best = words.maxOfOrNull { wordScore(token, it) } ?: -1
                if (best <= 0) {
                    allMatch = false
                    break
                }
                scoreSum += best
            }
            if (allMatch) {
                return 400 + (scoreSum / qTokens.size)
            }
        }

        // 6. Subsequence match (e.g. "hwdec" -> "hardware decoding")
        val subseq = subsequenceScore(q, t)
        if (subseq > 0) return subseq

        // 7. Typo match against individual words (Levenshtein distance)
        val bestTypoScore = words.maxOfOrNull { wordTypoScore(q, it) } ?: -1
        if (bestTypoScore > 0) return bestTypoScore

        // 8. Typo match against target as whole if lengths are comparable
        if (abs(q.length - t.length) <= 3) {
            val wholeTypoScore = wordTypoScore(q, t)
            if (wholeTypoScore > 0) return wholeTypoScore
        }

        return -1
    }

    private fun wordScore(token: String, word: String): Int {
        if (word == token) return 100
        if (word.startsWith(token)) return 80
        if (word.contains(token)) return 60
        val typo = wordTypoScore(token, word)
        if (typo > 0) return typo
        return -1
    }

    private fun subsequenceScore(query: String, target: String): Int {
        if (query.length < 2) return -1
        var qIdx = 0
        var tIdx = 0
        var consecutive = 0
        var bonus = 0

        while (qIdx < query.length && tIdx < target.length) {
            if (query[qIdx] == target[tIdx]) {
                consecutive++
                bonus += 10 + (consecutive * 5)
                if (tIdx == 0 || target[tIdx - 1] in " -_/") {
                    bonus += 25 // Match at word boundary
                }
                qIdx++
            } else {
                consecutive = 0
            }
            tIdx++
        }

        return if (qIdx == query.length) {
            250 + bonus.coerceAtMost(150)
        } else {
            -1
        }
    }

    private fun wordTypoScore(query: String, word: String): Int {
        val qLen = query.length
        val wLen = word.length
        // Only attempt typo matching if query has at least 3 characters
        if (qLen < 3) return -1

        val maxAllowedDistance = when {
            qLen <= 4 -> 1
            qLen <= 8 -> 2
            else -> 3
        }

        val dist = boundedLevenshtein(query, word, maxAllowedDistance)
        if (dist in 1..maxAllowedDistance) {
            return 320 - (dist * 70)
        }
        return -1
    }

    private fun boundedLevenshtein(s1: String, s2: String, maxLimit: Int): Int {
        if (abs(s1.length - s2.length) > maxLimit) return -1
        val m = s1.length
        val n = s2.length

        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,       // deletion
                    curr[j - 1] + 1,   // insertion
                    prev[j - 1] + cost // substitution
                )
                minInRow = minOf(minInRow, curr[j])
            }
            if (minInRow > maxLimit) return -1
            val temp = prev
            prev = curr
            curr = temp
        }
        return if (prev[n] <= maxLimit) prev[n] else -1
    }
}
