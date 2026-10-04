package com.veyra.app.nativeengine

interface NativeGenerationCallback {
    fun onToken(token: String)
    fun onError(message: String)
    fun onComplete()
}

object NativeLlama {
    init {
        System.loadLibrary("veyra_native")
    }

    /** Returns an empty string on success, otherwise a user-readable error. */
    external fun nativeLoadModel(path: String): String
    external fun nativeUnloadModel()
    external fun nativeStop()
    external fun nativeGenerate(prompt: String, callback: NativeGenerationCallback)
}
