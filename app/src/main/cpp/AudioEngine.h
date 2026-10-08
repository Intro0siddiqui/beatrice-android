#pragma once

#include <oboe/Oboe.h>
#include "BeatriceEngine.h"
#include <memory>
#include <atomic>

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();
    bool isRunning() const { return mIsRunning.load(); }

    BeatriceEngine* getBeatriceEngine() { return mBeatriceEngine.get(); }

    // Oboe Audio Callback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames
    ) override;

    void onErrorAfterClose(
        oboe::AudioStream *audioStream,
        oboe::Result result
    ) override;

    float getMeasuredLatencyMs() const { return mMeasuredLatencyMs.load(); }

private:
    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;
    std::unique_ptr<BeatriceEngine> mBeatriceEngine;

    std::atomic<bool> mIsRunning{false};
    std::atomic<float> mMeasuredLatencyMs{0.0f};

    // Buffer for reading input frames in callback
    std::vector<float> mInputBuffer;
    std::vector<float> mProcessBuffer;

    static constexpr int32_t kSampleRate = 24000; // Beatrice native 24 kHz
    static constexpr int32_t kChannelCount = 1;   // Mono
};
