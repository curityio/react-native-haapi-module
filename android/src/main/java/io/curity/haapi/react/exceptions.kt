package io.curity.haapi.react

class FailedHaapiRequestException(message: String, e: Throwable?) : Exception(e)
class FailedTokenManagerRequestException(message: String, e: Throwable?) : Exception(e)
class HaapiNotInitializedException(message: String = "Module not initialized") : Exception()
