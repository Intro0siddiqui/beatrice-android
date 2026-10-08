#include <jni.h>
#include <string>
#include <memory>
#include "AudioEngine.h"

static std::unique_ptr<AudioEngine> gAudioEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_initEngine(
    JNIEnv *env,
    jobject /* this */
) {
    if (!gAudioEngine) {
        gAudioEngine = std::make_unique<AudioEngine>();
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_loadModel(
    JNIEnv *env,
    jobject /* this */,
    jstring modelDirPath
) {
    if (!gAudioEngine) return JNI_FALSE;

    const char *nativePath = env->GetStringUTFChars(modelDirPath, nullptr);
    bool success = gAudioEngine->getBeatriceEngine()->loadModel(nativePath);
    env->ReleaseStringUTFChars(modelDirPath, nativePath);

    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_startAudio(
    JNIEnv *env,
    jobject /* this */
) {
    if (!gAudioEngine) return JNI_FALSE;
    return gAudioEngine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_stopAudio(
    JNIEnv *env,
    jobject /* this */
) {
    if (gAudioEngine) {
        gAudioEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_setPitchShift(
    JNIEnv *env,
    jobject /* this */,
    jfloat semitones
) {
    if (gAudioEngine) {
        gAudioEngine->getBeatriceEngine()->setPitchShift(semitones);
    }
}

JNIEXPORT void JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_setNoiseGate(
    JNIEnv *env,
    jobject /* this */,
    jfloat thresholdDb
) {
    if (gAudioEngine) {
        gAudioEngine->getBeatriceEngine()->setNoiseGate(thresholdDb);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_getInputLevel(
    JNIEnv *env,
    jobject /* this */
) {
    if (!gAudioEngine) return 0.0f;
    return gAudioEngine->getBeatriceEngine()->getInputLevel();
}

JNIEXPORT jfloat JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_getOutputLevel(
    JNIEnv *env,
    jobject /* this */
) {
    if (!gAudioEngine) return 0.0f;
    return gAudioEngine->getBeatriceEngine()->getOutputLevel();
}

JNIEXPORT jfloat JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_getLatencyMs(
    JNIEnv *env,
    jobject /* this */
) {
    if (!gAudioEngine) return 0.0f;
    return gAudioEngine->getMeasuredLatencyMs();
}

JNIEXPORT void JNICALL
Java_com_introcarbon_beatrice_jni_BeatriceJni_destroyEngine(
    JNIEnv *env,
    jobject /* this */
) {
    if (gAudioEngine) {
        gAudioEngine->stop();
        gAudioEngine.reset();
    }
}

} // extern "C"
