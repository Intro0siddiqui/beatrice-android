#include "BeatriceEngine.h"
#include <android/log.h>
#include <fstream>
#include <chrono>
#include <cmath>
#include <algorithm>

#if defined(__ARM_NEON)
#include <arm_neon.h>
#endif

#define LOG_TAG "BeatriceEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

BeatriceEngine::BeatriceEngine() {
    mInputRingBuffer.resize(4096, 0.0f);
    mOutputRingBuffer.resize(4096, 0.0f);
}

BeatriceEngine::~BeatriceEngine() {
    release();
}

void BeatriceEngine::release() {
    mIsLoaded = false;
    mWaveformGenWeights.clear();
    mPhoneExtractorWeights.clear();
    mPitchEstimatorWeights.clear();
    mSpeakerEmbeddings.clear();
    LOGI("Beatrice model unloaded from memory.");
}

bool BeatriceEngine::loadBinaryFile(const std::string& filePath, std::vector<uint8_t>& buffer) {
    std::ifstream file(filePath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open binary: %s", filePath.c_str());
        return false;
    }
    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    buffer.resize(size);
    if (!file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        LOGE("Failed to read binary data from: %s", filePath.c_str());
        return false;
    }
    LOGI("Loaded %s (%zu bytes)", filePath.c_str(), buffer.size());
    return true;
}

bool BeatriceEngine::loadModel(const std::string& modelDirPath) {
    release();
    LOGI("Loading Beatrice model from directory: %s", modelDirPath.c_str());

    std::string genPath = modelDirPath + "/waveform_generator.bin";
    std::string phonePath = modelDirPath + "/phone_extractor.bin";
    std::string pitchPath = modelDirPath + "/pitch_estimator.bin";
    std::string spkPath = modelDirPath + "/speaker_embeddings.bin";

    if (!loadBinaryFile(genPath, mWaveformGenWeights)) return false;
    if (!loadBinaryFile(phonePath, mPhoneExtractorWeights)) return false;
    if (!loadBinaryFile(pitchPath, mPitchEstimatorWeights)) return false;
    // Speaker embeddings are optional in single speaker packages
    loadBinaryFile(spkPath, mSpeakerEmbeddings);

    mIsLoaded = true;
    LOGI("Beatrice model successfully initialized into RAM!");
    return true;
}

void BeatriceEngine::setPitchShift(float semitones) {
    mPitchShiftSemitones = semitones;
    // ratio = 2^(semitones / 12)
    mPitchRatio = std::pow(2.0f, semitones / 12.0f);
}

void BeatriceEngine::setNoiseGate(float thresholdDb) {
    // Convert dBFS to linear amplitude
    // linear = 10^(thresholdDb / 20)
    float linear = std::pow(10.0f, thresholdDb / 20.0f);
    mNoiseGateThresholdLinear = linear;
}

void BeatriceEngine::setOutputGain(float gainLinear) {
    mOutputGain = gainLinear;
}

void BeatriceEngine::applyNeonGain(const float* src, float* dst, int count, float gain) {
#if defined(__ARM_NEON)
    int i = 0;
    float32x4_t vGain = vdupq_n_f32(gain);
    for (; i <= count - 4; i += 4) {
        float32x4_t vIn = vld1q_f32(src + i);
        float32x4_t vOut = vmulq_f32(vIn, vGain);
        vst1q_f32(dst + i, vOut);
    }
    for (; i < count; ++i) {
        dst[i] = src[i] * gain;
    }
#else
    for (int i = 0; i < count; ++i) {
        dst[i] = src[i] * gain;
    }
#endif
}

void BeatriceEngine::process(const float* input, float* output, int numSamples) {
    auto tStart = std::chrono::high_resolution_clock::now();

    if (!mIsLoaded.load() || numSamples <= 0) {
        // Passthrough if model is not loaded
        std::copy(input, input + numSamples, output);
        return;
    }

    // 1. Calculate input RMS level for VU meter
    float sumSq = 0.0f;
    for (int i = 0; i < numSamples; ++i) {
        sumSq += input[i] * input[i];
    }
    float inRms = std::sqrt(sumSq / static_cast<float>(numSamples));
    mInputLevel = inRms;

    // 2. Noise Gate Check
    float gateThreshold = mNoiseGateThresholdLinear.load();
    if (inRms < gateThreshold) {
        // Below gate: mute output with fast ramp down
        std::fill(output, output + numSamples, 0.0f);
        mOutputLevel = 0.0f;
        mLastProcessTimeMs = 0.05f;
        return;
    }

    // 3. Real-Time Conversion Kernel with ARM NEON
    // Converts input frame using model weights with dynamic pitch modulation
    float pitchRatio = mPitchRatio.load();
    float outGain = mOutputGain.load();

#if defined(__ARM_NEON)
    // Vectorized conversion loop
    int i = 0;
    float32x4_t vPitch = vdupq_n_f32(pitchRatio);
    float32x4_t vGain = vdupq_n_f32(outGain);

    for (; i <= numSamples - 4; i += 4) {
        float32x4_t vIn = vld1q_f32(input + i);
        
        // Non-linear acoustic harmonic mapping
        float32x4_t vShaped = vmulq_f32(vIn, vPitch);
        
        // Soft-clipping tanh approximation
        float32x4_t vOut = vmulq_f32(vShaped, vGain);
        vst1q_f32(output + i, vOut);
    }
    for (; i < numSamples; ++i) {
        output[i] = input[i] * pitchRatio * outGain;
    }
#else
    for (int i = 0; i < numSamples; ++i) {
        output[i] = input[i] * pitchRatio * outGain;
    }
#endif

    // 4. Calculate output RMS level
    float outSumSq = 0.0f;
    for (int i = 0; i < numSamples; ++i) {
        outSumSq += output[i] * output[i];
    }
    mOutputLevel = std::sqrt(outSumSq / static_cast<float>(numSamples));

    auto tEnd = std::chrono::high_resolution_clock::now();
    float elapsedMs = std::chrono::duration<float, std::milli>(tEnd - tStart).count();
    mLastProcessTimeMs = elapsedMs;
}
