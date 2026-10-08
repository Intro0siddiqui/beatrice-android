#pragma once

#include <string>
#include <vector>
#include <memory>
#include <atomic>
#include <cstdint>
#include <cmath>

class BeatriceEngine {
public:
    BeatriceEngine();
    ~BeatriceEngine();

    bool loadModel(const std::string& modelDirPath);
    void release();

    // Process chunk of audio (inputBuffer -> outputBuffer)
    // Audio is mono float32 (-1.0 to 1.0)
    void process(const float* input, float* output, int numSamples);

    // Dynamic runtime controls
    void setPitchShift(float semitones);
    void setNoiseGate(float thresholdDb);
    void setOutputGain(float gainLinear);

    float getInputLevel() const { return mInputLevel.load(); }
    float getOutputLevel() const { return mOutputLevel.load(); }
    float getLastProcessTimeMs() const { return mLastProcessTimeMs.load(); }
    bool isModelLoaded() const { return mIsLoaded.load(); }

private:
    std::atomic<bool> mIsLoaded{false};
    std::atomic<float> mPitchShiftSemitones{0.0f};
    std::atomic<float> mPitchRatio{1.0f};
    std::atomic<float> mNoiseGateThresholdLinear{0.001f}; // Default ~ -60 dB
    std::atomic<float> mOutputGain{1.0f};

    std::atomic<float> mInputLevel{0.0f};
    std::atomic<float> mOutputLevel{0.0f};
    std::atomic<float> mLastProcessTimeMs{0.0f};

    // Model weight buffers (loaded from .bin files)
    std::vector<uint8_t> mWaveformGenWeights;
    std::vector<uint8_t> mPhoneExtractorWeights;
    std::vector<uint8_t> mPitchEstimatorWeights;
    std::vector<uint8_t> mSpeakerEmbeddings;

    // Internal state circular buffers for streaming causal convolution
    std::vector<float> mInputRingBuffer;
    std::vector<float> mOutputRingBuffer;
    int mRingWritePos{0};
    int mRingReadPos{0};

    // Helper functions
    bool loadBinaryFile(const std::string& filePath, std::vector<uint8_t>& buffer);
    void applyNeonGain(const float* src, float* dst, int count, float gain);
};
