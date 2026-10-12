package com.x500x.cursimple.core.kernel.model

/** Image references are portable content hashes, never filesystem paths or gallery grants. */
data class MemoImageRef(val id: String, val alt: String) {
    companion object {
        private val pattern = Regex("!\\[([^]\\n]*)]\\(cursimple-image:([a-f0-9]{64})\\)")
        fun validId(id: String): Boolean = id.length == 64 && id.all { it in 'a'..'f' || it in '0'..'9' }
        fun parse(line: String): MemoImageRef? = pattern.matchEntire(line.trim())?.let {
            MemoImageRef(it.groupValues[2], it.groupValues[1])
        }
        fun ids(body: String): Set<String> = pattern.findAll(body).map { it.groupValues[2] }.toSet()
        fun markdown(id: String, alt: String): String {
            require(validId(id))
            val label = alt.replace(Regex("[\\[\\]\\\\\\r\\n]"), " ").trim().take(120)
            return "![$label](cursimple-image:$id)"
        }
    }
}
