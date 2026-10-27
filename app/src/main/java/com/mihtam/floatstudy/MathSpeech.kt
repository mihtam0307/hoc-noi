package com.mihtam.floatstudy

/** Chuyển công thức / ký tự đặc biệt thành chữ để máy đọc tiếng Việt. */
object MathSpeech {
    private val greek = mapOf(
        'α' to "an-pha", 'β' to "bê-ta", 'γ' to "ga-ma", 'δ' to "đen-ta", 'Δ' to "đen-ta",
        'ε' to "ép-xi-lon", 'η' to "ê-ta", 'θ' to "tê-ta", 'λ' to "lam-đa", 'μ' to "muy",
        'ν' to "nuy", 'π' to "pi", 'ρ' to "rô", 'σ' to "xích-ma", 'τ' to "tau",
        'φ' to "phi", 'Φ' to "phi", 'ψ' to "psi", 'ω' to "ô-mê-ga", 'Ω' to "ô-mê-ga", 'Σ' to "tổng"
    )

    private val sym = mapOf(
        '+' to " cộng ", '=' to " bằng ", '<' to " nhỏ hơn ", '>' to " lớn hơn ",
        '×' to " nhân ", '÷' to " chia ", '·' to " nhân ", '⋅' to " nhân ",
        '±' to " cộng trừ ", '∓' to " trừ cộng ", '≠' to " khác ", '≈' to " xấp xỉ ",
        '≤' to " nhỏ hơn hoặc bằng ", '≥' to " lớn hơn hoặc bằng ", '≡' to " đồng nhất ",
        '∝' to " tỉ lệ thuận với ", '√' to " căn bậc hai của ", '∞' to " vô cùng ",
        '∑' to " tổng ", '∫' to " tích phân ", '∂' to " đạo hàm riêng ", '∇' to " na-bla ",
        '°' to " độ ", '%' to " phần trăm ", '‰' to " phần nghìn ",
        '→' to " tới ", '⇒' to " suy ra ", '⇔' to " tương đương ", '↔' to " hai chiều ",
        '∈' to " thuộc ", '∉' to " không thuộc ", '⊂' to " là tập con của ",
        '∪' to " hợp ", '∩' to " giao ", '∀' to " với mọi ", '∃' to " tồn tại ", '∅' to " tập rỗng ",
        '∠' to " góc ", '⊥' to " vuông góc ", '∥' to " song song ", '‖' to " song song ",
        '∴' to " vậy ", '∵' to " vì ", '&' to " và ", '@' to " a còng ", '#' to " số ",
        '~' to " khoảng ", '•' to " ", '`' to " ", '′' to " phẩy ", '″' to " phẩy phẩy ",
        '{' to " ", '}' to " "
    )

    // Chỉ dùng khi dòng được nhận là công thức
    private val mathSym = mapOf(
        '(' to " mở ngoặc ", ')' to " đóng ngoặc ", '[' to " mở ngoặc vuông ", ']' to " đóng ngoặc vuông ",
        '|' to " gạch đứng ", '/' to " chia ", '*' to " nhân ", '^' to " mũ ", '-' to " trừ ", '_' to " "
    )

    private val supMap = mapOf(
        '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4', '⁵' to '5',
        '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9', '⁻' to '-', 'ⁿ' to 'n'
    )

    private val latexWords = mapOf(
        "alpha" to "an-pha", "beta" to "bê-ta", "gamma" to "ga-ma", "delta" to "đen-ta", "Delta" to "đen-ta",
        "epsilon" to "ép-xi-lon", "varepsilon" to "ép-xi-lon", "eta" to "ê-ta", "theta" to "tê-ta",
        "lambda" to "lam-đa", "mu" to "muy", "nu" to "nuy", "pi" to "pi", "rho" to "rô", "sigma" to "xích-ma",
        "tau" to "tau", "phi" to "phi", "varphi" to "phi", "Phi" to "phi", "omega" to "ô-mê-ga", "Omega" to "ô-mê-ga",
        "times" to "nhân", "cdot" to "nhân", "div" to "chia", "pm" to "cộng trừ", "mp" to "trừ cộng",
        "le" to "nhỏ hơn hoặc bằng", "leq" to "nhỏ hơn hoặc bằng", "ge" to "lớn hơn hoặc bằng", "geq" to "lớn hơn hoặc bằng",
        "ne" to "khác", "neq" to "khác", "approx" to "xấp xỉ", "infty" to "vô cùng", "sum" to "tổng", "int" to "tích phân",
        "partial" to "đạo hàm riêng", "nabla" to "na-bla", "to" to "tới", "rightarrow" to "tới",
        "Rightarrow" to "suy ra", "Leftrightarrow" to "tương đương", "circ" to "độ", "degree" to "độ",
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "cot" to "cot", "log" to "log", "ln" to "ln", "lim" to "lim",
        "hbar" to "h gạch", "propto" to "tỉ lệ thuận với", "perp" to "vuông góc", "parallel" to "song song",
        "angle" to "góc", "prime" to "phẩy", "ell" to "l", "cdots" to "", "ldots" to "", "dots" to ""
    )

    private val funcWords = mapOf(
        "cos" to "cô-sin", "cot" to "cô-tang", "tan" to "tang", "log" to "lô-ga-rít", "ln" to "lốc nê-pe", "lim" to "giới hạn"
    )

    private val unitName = mapOf(
        "km" to "ki-lô-mét", "m" to "mét", "cm" to "xen-ti-mét", "mm" to "mi-li-mét",
        "s" to "giây", "h" to "giờ", "kg" to "ki-lô-gam", "g" to "gam", "N" to "niu-tơn",
        "J" to "jun", "W" to "oát", "Hz" to "héc", "V" to "vôn", "A" to "am-pe", "C" to "cu-lông",
        "Pa" to "pát-can", "rad" to "ra-đian", "mol" to "mol", "K" to "ken-vin", "F" to "fa-ra",
        "T" to "tét-la", "Ω" to "ôm"
    )
    private val lengthUnits = setOf("m", "cm", "mm", "km")
    private const val U = "km|cm|mm|kg|rad|mol|Hz|Pa|m|g|s|h|N|J|W|V|A|C|K|F|T|Ω"

    private val unitCombo = Regex("(?<!\\p{L})($U)/($U)(²|\\^2|2|³|\\^3)?(?![\\p{L}\\p{N}])")
    private val unitAlone = Regex("(\\p{N})\\s*($U)(²|³|\\^2|\\^3)?(?!\\p{L})")
    private val supRe = Regex("[⁰¹²³⁴⁵⁶⁷⁸⁹⁻ⁿ]+")
    private val subRe = Regex("[₀-₉]+")
    private val powRe = Regex("\\^\\s*\\{([^{}]*)\\}|\\^\\s*(-?[\\p{L}\\p{N}]+)")
    private val subscrRe = Regex("_\\s*\\{([^{}]*)\\}|_\\s*([\\p{L}\\p{N}])")
    private val minusRe = Regex("(?<=[\\p{L}\\p{N})\\]])\\s*-\\s*(?=[\\p{L}\\p{N}(\\[√])")
    private val negRe = Regex("(^|[\\s(\\[=<>+*/,;:±×÷])-(?=[\\p{L}\\p{N}(√])")
    private val divRe = Regex("(?<=[\\p{L}\\p{N})\\]])\\s*/\\s*(?=[\\p{L}\\p{N}(√])")
    private val funcRe = Regex("(?<!\\p{L})(cos|cot|tan|log|ln|lim)(?!\\p{L})")
    private val mathRe = Regex(
        """[=^\\√∑∫≤≥≠≈×÷±∞π²³⁰-⁹₀-₉∂∇∝∈∠⊥]|\p{N}\s*[+*/]\s*[\p{L}\p{N}(]|\p{L}\s*\+\s*\p{L}|\p{N}\s+-\s+\p{N}|(^|\s)-\p{N}"""
    )

    private fun un(u: String): String = unitName[u] ?: u

    private fun expWords(unit: String, e: String): String {
        val len = unit in lengthUnits
        return when {
            e.contains('2') -> if (len) " vuông" else " bình phương"
            e.contains('3') -> if (len) " khối" else " lập phương"
            else -> ""
        }
    }

    private fun units(s0: String): String {
        var s = unitCombo.replace(s0) { m ->
            if (m.value == "N/A") m.value
            else " ${un(m.groupValues[1])} trên ${un(m.groupValues[2])}${expWords(m.groupValues[2], m.groupValues[3])} "
        }
        s = unitAlone.replace(s) { m ->
            "${m.groupValues[1]} ${un(m.groupValues[2])}${expWords(m.groupValues[2], m.groupValues[3])} "
        }
        return s
    }

    private fun latex(input: String): String {
        var s = input.replace("\\%", "%").replace("\\\\", " ")
        s = Regex("\\\\(?:left|right|bigl|bigr|big|Big)(?![a-zA-Z])").replace(s, "")
        s = Regex("\\\\(?:,|;|:|!|quad|qquad|displaystyle)").replace(s, " ")
        val frac = Regex("\\\\[dt]?frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}")
        repeat(6) { s = frac.replace(s) { m -> " ${m.groupValues[1]} phần ${m.groupValues[2]} " } }
        s = Regex("\\\\sqrt\\s*\\[([^\\]]*)\\]\\s*\\{([^{}]*)\\}")
            .replace(s) { m -> " căn bậc ${m.groupValues[1]} của ${m.groupValues[2]} " }
        s = Regex("\\\\sqrt\\s*\\{([^{}]*)\\}")
            .replace(s) { m -> " căn bậc hai của ${m.groupValues[1]} " }
        s = Regex("\\\\(?:vec|overrightarrow)\\s*\\{([^{}]*)\\}")
            .replace(s) { m -> " vectơ ${m.groupValues[1]} " }
        s = Regex("\\\\(?:text|mathrm|mathbf|mathit|operatorname|textbf|boldsymbol)\\s*\\{([^{}]*)\\}")
            .replace(s) { m -> " ${m.groupValues[1]} " }
        s = Regex("\\\\([a-zA-Z]+)").replace(s) { m ->
            " ${latexWords[m.groupValues[1]] ?: m.groupValues[1]} "
        }
        return s.replace("\\", " ")
    }

    fun speak(line: String): String {
        var s = line.replace('−', '-')
            .replace("**", "")
            .replace(Regex("^\\s*#+\\s+"), "")
            .replace(Regex("^\\s*[-*•]\\s+"), "")
        val hasTex = s.contains('\\')
        val math = mathRe.containsMatchIn(s)
        if (hasTex) s = latex(s.replace("$", " "))
        s = units(s)
        s = supRe.replace(s) { m -> "^" + m.value.map { supMap[it] ?: ' ' }.joinToString("") }
        s = subRe.replace(s) { m -> " " + m.value.map { '0' + (it - '₀') }.joinToString("") + " " }
        s = powRe.replace(s) { m ->
            val e = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
            when (e) {
                "2" -> " bình phương "
                "3" -> " lập phương "
                else -> " mũ ${e.replace("-", "trừ ")} "
            }
        }
        s = subscrRe.replace(s) { m -> " ${m.groupValues[1].ifEmpty { m.groupValues[2] }} " }
        if (math) {
            s = minusRe.replace(s, " trừ ")
            s = negRe.replace(s) { m -> m.groupValues[1] + "âm " }
            s = divRe.replace(s, " phần ")
            s = funcRe.replace(s) { m -> funcWords[m.groupValues[1]] ?: m.value }
        }
        val sb = StringBuilder()
        for (ch in s) {
            val g = greek[ch]
            when {
                g != null -> sb.append(' ').append(g).append(' ')
                sym.containsKey(ch) -> sb.append(sym[ch])
                math && mathSym.containsKey(ch) -> sb.append(mathSym[ch])
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
