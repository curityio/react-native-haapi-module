package io.curity.haapi.react

open class HaapiException(message: String, e: Throwable?) : Exception(message, e)
class FailedHaapiRequestException(message: String, e: Throwable?) : HaapiException(message, e)
class FailedTokenManagerRequestException(message: String, e: Throwable?) : HaapiException(message, e)
class HaapiNotInitializedException(message: String = "Module not initialized") : HaapiException(message, null)
