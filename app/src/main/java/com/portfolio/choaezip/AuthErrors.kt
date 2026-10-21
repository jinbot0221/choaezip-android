package com.portfolio.choaezip

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.functions.FirebaseFunctionsException

internal fun authErrorMessage(e: Exception): String = when(e) {
        is com.google.firebase.FirebaseNetworkException -> "서버에 연결할 수 없어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
        is com.google.firebase.FirebaseTooManyRequestsException -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
        is FirebaseAuthException -> if (e.errorCode == "ERROR_NETWORK_REQUEST_FAILED") "연결을 확인하고 다시 시도해 주세요." else "인증에 실패했어요. 입력 정보와 로그인 방법을 확인해 주세요."
        is FirebaseFunctionsException -> when(e.code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "로그인이 필요해요. 다시 로그인해 주세요."
            FirebaseFunctionsException.Code.UNAVAILABLE -> "서버에 연결할 수 없어요. 네트워크와 Firebase 실행 상태를 확인해 주세요."
            FirebaseFunctionsException.Code.ABORTED -> "다른 곳에서 변경됐어요. 입력은 보관되므로 최신 내용을 확인한 뒤 다시 시도해 주세요."
            else -> e.message ?: "요청을 처리하지 못했어요."
        }
        else -> e.localizedMessage ?: "처리 중 문제가 생겼어요. 다시 시도해 주세요."
    }
