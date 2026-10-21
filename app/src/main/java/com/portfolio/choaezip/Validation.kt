package com.portfolio.choaezip

object Validation {
    fun email(value: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
    fun nickname(value: String) = value.trim().length in 2..20
    fun password(value: String) = value.length in 8..64
    fun profileErrors(nickname: String, agree: Boolean): Map<String, String> = buildMap {
        if (!nickname(nickname)) put("nickname", "닉네임은 2~20자로 입력해 주세요.")
        if (!agree) put("agree", "필수 이용약관 및 개인정보 처리에 동의해 주세요.")
    }
    fun signupErrors(email: String, password: String, confirm: String, nickname: String, agree: Boolean): Map<String, String> = buildMap {
        if (!nickname(nickname)) put("nickname", "닉네임은 2~20자로 입력해 주세요.")
        if (!email(email.trim())) put("email", "이메일 주소 형식을 확인해 주세요.")
        if (!password(password)) put("password", "비밀번호는 8~64자로 입력해 주세요.")
        if (password != confirm) put("confirm", "비밀번호 확인이 일치하지 않아요.")
        if (!agree) put("agree", "필수 이용약관 및 개인정보 처리에 동의해 주세요.")
    }
    fun content(title: String, body: String, record: Boolean): String? = when {
        title.trim().length !in 2..(if (record) 50 else 80) -> "제목은 2~${if (record) 50 else 80}자로 입력해 주세요."
        body.trim().length !in 1..(if (record) 2000 else 3000) -> "본문은 1~${if (record) 2000 else 3000}자로 입력해 주세요."
        else -> null
    }
}
