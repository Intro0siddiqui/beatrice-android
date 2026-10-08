#include "AudioEngine.h"
#include <android/log.h>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    mBeatriceEngine = std::make_unique<BeatriceEngine>();
    mInputBuffer.resize(1024, 0.0f);
    mProcessBuffer.resize(1024, 0.0f);
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start() {
    if (mIsRunning.load()) {
        return true;
    }

    LOGI("Opening low-latency audio streams via Oboe...");

    // 1. Build Output Stream (Headphones)
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(kChannelCount)
        ->setSampleRate(kSampleRate)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = outBuilder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open output stream: %s", oboe::convertToText(result));
        return false;
    }

    // 2. Build Input Stream (Headset Mic)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(kChannelCount)
        ->setSampleRate(kSampleRate)
        ->setInputPreset(oboe::InputPreset::VoiceCommunication);

    result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open input stream: %s", oboe::convertToText(result));
        mOutputStream->close();
        return false;
    }

    // 3. Start Streams
    result = mInputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start input stream: %s", oboe::convertToText(result));
        mInputStream->close();
        mOutputStream->close();
        return false;
    }

    result = mOutputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start output stream: %s", oboe::convertToText(result));
        mInputStream->stop();
        mInputStream->close();
        mOutputStream->close();
        return false;
    }

    // Calculate buffer latency
    int32_t bufferSize = mOutputStream->getBufferSizeInFrames();
    float latencyMs = (static_cast<float>(bufferSize) / static_cast<float>(kSampleRate)) * 1000.0f;
    mMeasuredLatencyMs = latencyMs;

    mIsRunning = true;
    LOGI("Audio engine started! Stream buffer size: %d frames (Latency: %.1f ms)", bufferSize, latencyMs);
    return true;
}

void AudioEngine::stop() {
    if (!mIsRunning.load()) {
        return;
    }

    mIsRunning = false;
    LOGI("Stopping audio engine streams...");

    if (mInputStream) {
        mInputStream->stop();
        mInputStream->close();
        mInputStream.reset();
    }

    if (mOutputStream) {
        mOutputStream->stop();
        mOutputStream->close();
        mOutputStream.reset();
    }

    LOGI("Audio engine successfully stopped.");
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames
) {
    float *output = static_cast<float*>(audioData);

    if (mInputBuffer.size() < static_cast<size_t>(numFrames)) {
        mInputBuffer.resize(numFrames, 0.0f);
        mProcessBuffer.resize(numFrames, 0.0f);
    }

    // Read microphone samples with 0 timeout (non-blocking)
    if (mInputStream) {
        auto readResult = mInputStream->read(mInputBuffer.data(), numFrames, 0);
        if (readResult.value() > 0) {
            int32_t framesRead = readResult.value();

            // Run real-time Beatrice inference
            mBeatriceEngine->process(mInputBuffer.data(), output, framesRead);

            // Zero pad any remaining frames
            if (framesRead < numFrames) {
                std::fill(output + framesRead, output + numFrames, 0.0f);
            }
            return oboe::DataCallbackResult::Continue;
        }
    }

    // If input is empty or underrun, output silence to prevent audio glitching
    std::fill(output, output + numFrames, 0.0f);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(
    oboe::AudioStream *audioStream,
    oboe::Result result
) {
    LOGE("Audio stream closed due to error: %s. Restarting...", oboe::convertToText(result));
    if (result == oboe::Result::ErrorDisconnected) {
        // e.g. headphones unplugged
        stop();
    }
}
