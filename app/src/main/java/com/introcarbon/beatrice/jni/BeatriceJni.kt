package com.introcarbon.beatrice.jni

object BeatriceJni {
    init {
        System.loadLibrary("beatrice_native")
    }

    external fun initEngine(): Boolean
    external fun loadModel(modelDirPath: String): Boolean
    external fun startAudio(): Boolean
    external fun stopAudio()
    external fun setPitchShift(semitones: Float)
    external fun setNoiseGate(thresholdDb: Float)
    external fun getInputLevel(): Float
    external fun getOutputLevel(): Float
    external fun getLatencyMs(): Float
    external fun destroyEngine()
}
