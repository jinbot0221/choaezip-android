package com.portfolio.choaezip

object Validation {
    fun email(value: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
    fun nickname(value: String) = value.trim().length in 2..20
    fun password(value: String) = value.length in 8..64
    fun content(title: String, body: String, record: Boolean): String? = when {
        title.trim().length !in 2..(if (record) 50 else 80) -> "제목은 2~${if (record) 50 else 80}자로 입력해 주세요."
        body.trim().length !in 1..(if (record) 2000 else 3000) -> "본문은 1~${if (record) 2000 else 3000}자로 입력해 주세요."
        else -> null
    }
}
