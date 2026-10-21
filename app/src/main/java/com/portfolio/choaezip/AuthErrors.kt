package com.portfolio.choaezip

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.functions.FirebaseFunctionsException

internal fun authErrorMessage(e: Exception): String = when(e) {
        is com.google.firebase.FirebaseNetworkException -> "서버에 연결할 수 없어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
        is com.google.firebase.FirebaseTooManyRequestsException -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
        is androidx.credentials.exceptions.GetCredentialProviderConfigurationException -> "Google 로그인 서비스를 사용할 수 없어요. 기기의 Google Play 서비스를 설치하거나 업데이트해 주세요."
        is androidx.credentials.exceptions.GetCredentialException -> "Google 계정 선택에 실패했어요. 기기 계정과 인터넷 연결을 확인하고, 계속 실패하면 Firebase의 앱 서명 SHA 및 Google 로그인 설정을 확인해 주세요."
        is FirebaseAuthException -> when(e.errorCode) {
            "ERROR_NETWORK_REQUEST_FAILED" -> "연결을 확인하고 다시 시도해 주세요."
            "ERROR_OPERATION_NOT_ALLOWED" -> "이 로그인 방법이 Firebase에서 비활성화되어 있어요. Authentication의 로그인 제공업체를 확인해 주세요."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "이미 가입된 이메일이에요. 이메일 로그인 또는 비밀번호 재설정을 이용해 주세요."
            "ERROR_WEAK_PASSWORD" -> "비밀번호가 Firebase의 비밀번호 정책에 맞지 않아요. 더 안전한 비밀번호로 다시 시도해 주세요."
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_IDP_RESPONSE" -> "인증 정보가 올바르지 않아요. 이메일 비밀번호 또는 Google 로그인 설정(SHA 인증서와 클라이언트 ID)을 확인해 주세요."
            else -> "인증에 실패했어요 (${e.errorCode}). 입력 정보와 Firebase 로그인 설정을 확인해 주세요."
        }
        is FirebaseFunctionsException -> when(e.code) {
            FirebaseFunctionsException.Code.NOT_FOUND -> "요청한 정보를 찾을 수 없어요."
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> "로그인이 필요해요. 다시 로그인해 주세요."
            FirebaseFunctionsException.Code.UNAVAILABLE -> "서버에 연결할 수 없어요. 네트워크와 Firebase 실행 상태를 확인해 주세요."
            FirebaseFunctionsException.Code.ABORTED -> "다른 곳에서 변경됐어요. 입력은 보관되므로 최신 내용을 확인한 뒤 다시 시도해 주세요."
            else -> e.message ?: "요청을 처리하지 못했어요."
        }
        else -> e.localizedMessage ?: "처리 중 문제가 생겼어요. 다시 시도해 주세요."
    }

internal fun profileErrorMessage(e: Exception): String =
    if(e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND)
        "Firebase 프로젝트에 서울 리전의 api Functions가 배포되어 있는지 확인해 주세요."
    else authErrorMessage(e)
