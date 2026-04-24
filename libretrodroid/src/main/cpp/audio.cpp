/*
 *     Copyright (C) 2019  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#include "log.h"

#include "audio.h"
#include <algorithm>
#include <cmath>
#include <memory>
#include <thread>

namespace libretrodroid {

Audio::Audio(
    int32_t sampleRate,
    double refreshRate,
    bool preferLowLatencyAudio,
    DeviceAudioProfile deviceAudioProfile
) {
    LOGI("Audio initialization has been called with input sample rate %d", sampleRate);

    contentRefreshRate = refreshRate;
    inputSampleRate = sampleRate;
    this->deviceAudioProfile = deviceAudioProfile;
    audioLatencySettings = findBestLatencySettings(shouldPreferLowLatencyAudio(preferLowLatencyAudio));
    initializeStream();
}

bool Audio::initializeStream() {
    LOGI("Using low latency stream: %d", audioLatencySettings->useLowLatencyStream);

    int32_t audioBufferSize = computeAudioBufferSize();
    const bool legacyAudioPath = usesLegacyAudioPath();

    oboe::AudioStreamBuilder builder;
    builder.setChannelCount(2);
    builder.setDirection(oboe::Direction::Output);
    builder.setFormat(oboe::AudioFormat::I16);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    if (deviceAudioProfile == DeviceAudioProfile::PS202) {
        builder.setAudioApi(oboe::AudioApi::OpenSLES);
    }

    // On pre-AAudio devices Oboe falls back to OpenSLES and otherwise defaults to 48 kHz.
    // Request the hardware rate we got from AudioManager to avoid a second 48 -> 44.1 kHz resample in AudioFlinger.
    if (legacyAudioPath && oboe::DefaultStreamValues::SampleRate > 0) {
        builder.setSampleRate(oboe::DefaultStreamValues::SampleRate);
    }

    const int32_t framesPerCallback = getFramesPerCallback(audioBufferSize);

    if (audioLatencySettings->useLowLatencyStream) {
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    } else {
        builder.setFramesPerCallback(framesPerCallback);
    }

    oboe::Result result = builder.openManagedStream(stream);
    if (result == oboe::Result::OK) {
        int32_t actualBufferSize = stream->getBufferSizeInFrames();
        if (legacyAudioPath) {
            auto bufferSizeResult = stream->setBufferSizeInFrames(stream->getBufferCapacityInFrames());
            if (bufferSizeResult == oboe::Result::OK) {
                actualBufferSize = bufferSizeResult.value();
            } else {
                LOGW(
                    "Failed to pin audio buffer size. Error: %s",
                    oboe::convertToText(bufferSizeResult.error())
                );
            }
        }
        LOGI(
            "Opened audio stream requestedRate=%d sampleRate=%d framesPerBurst=%d framesPerCallback=%d bufferSize=%d bufferCapacity=%d",
            builder.getSampleRate(),
            stream->getSampleRate(),
            stream->getFramesPerBurst(),
            framesPerCallback,
            actualBufferSize,
            stream->getBufferCapacityInFrames()
        );
        baseConversionFactor = (double) inputSampleRate / stream->getSampleRate();
        fifoBuffer = std::make_unique<oboe::FifoBuffer>(2, audioBufferSize);
        temporaryAudioBuffer = std::unique_ptr<int16_t[]>(new int16_t[audioBufferSize]);
        latencyTuner = legacyAudioPath ? nullptr : std::make_unique<oboe::LatencyTuner>(*stream);
        streamStarted = false;
        return true;
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
        stream = nullptr;
        latencyTuner = nullptr;
        return false;
    }
}

std::unique_ptr<Audio::AudioLatencySettings> Audio::findBestLatencySettings(bool preferLowLatencyAudio) {
    if (usesLegacyAudioPath()) {
        return std::make_unique<AudioLatencySettings>(getOpenSLESLatencySettings());
    } else if (preferLowLatencyAudio) {
        return std::make_unique<AudioLatencySettings>(LOW_LATENCY_SETTINGS);
    } else {
        return std::make_unique<AudioLatencySettings>(DEFAULT_LATENCY_SETTINGS);
    }
}

int32_t Audio::computeAudioBufferSize() {
    double maxLatency = computeMaximumLatency();
    LOGI("Average audio latency set to: %f ms", maxLatency * 0.5);
    double sampleRateDivisor = 500.0 / maxLatency;
    return roundToEven(inputSampleRate / sampleRateDivisor);
}

double Audio::computeMaximumLatency() const {
    double maxLatency = (audioLatencySettings->bufferSizeInVideoFrames / contentRefreshRate) * 1000;
    return std::max(maxLatency, 32.0);
}

void Audio::start() {
    startRequested = true;
    if (fifoResetPending) {
        resetFifo();
        fifoResetPending = false;
    }
    startStreamIfReady();
}

void Audio::stop() {
    startRequested = false;
    if (stream != nullptr) {
        stream->requestStop();
    }
    streamStarted = false;
    fifoResetPending = true;
}

void Audio::write(const int16_t *data, size_t frames) {
    waitForWritableAudio(frames * 2);
    fifoBuffer->write(data, frames * 2);
    startStreamIfReady();
}

void Audio::setPlaybackSpeed(const double newPlaybackSpeed) {
    playbackSpeed = newPlaybackSpeed;
}

oboe::DataCallbackResult Audio::onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {
    double dynamicBufferFactor = usesDynamicBufferSync() ? computeDynamicBufferConversionFactor(0.001 * numFrames) : 1.0;
    double finalConversionFactor = baseConversionFactor * dynamicBufferFactor * playbackSpeed;

    // When using low-latency stream, numFrames is very low (~100) and the dynamic buffer scaling doesn't work with rounding.
    // By keeping track of the "fractional" frames we can keep the error smaller.
    framesToSubmit += numFrames * finalConversionFactor;
    int32_t currentFramesToSubmit = std::round(framesToSubmit);
    framesToSubmit -= currentFramesToSubmit;

    fifoBuffer->readNow(temporaryAudioBuffer.get(), currentFramesToSubmit * 2);

    auto outputArray = reinterpret_cast<int16_t *>(audioData);
    resampler.resample(temporaryAudioBuffer.get(), currentFramesToSubmit, outputArray, numFrames);

    if (latencyTuner != nullptr) {
        latencyTuner->tune();
    }

    return oboe::DataCallbackResult::Continue;
}

// To prevent audio buffer overruns or underruns we set up a PI controller. The idea is to run the
// audio slower when the buffer is empty and faster when it's full.
double Audio::computeDynamicBufferConversionFactor(double dt) {
    double framesCapacityInBuffer = fifoBuffer->getBufferCapacityInFrames();
    double framesAvailableInBuffer = fifoBuffer->getFullFramesAvailable();

    // Error is represented by normalized distance to half buffer utilization. Range [-1.0, 1.0]
    double errorMeasure = (framesCapacityInBuffer - 2.0f * framesAvailableInBuffer) / framesCapacityInBuffer;

    errorIntegral += errorMeasure * dt;

    // Wikipedia states that human ear resolution is around 3.6 Hz within the octave of 1000–2000 Hz.
    // This changes continuously, so we should try to keep it a very low value.
    double proportionalAdjustment = std::clamp(kp * errorMeasure, -maxp, maxp);

    // Ki is a lot lower, so it's safe if it exceeds the ear threshold. Hopefully convergence will
    // be slow enough to be not perceptible. We need to battle test this value.
    double integralAdjustment = std::clamp(ki * errorIntegral, -maxi, maxi);

    double finalAdjustment = proportionalAdjustment + integralAdjustment;

    LOGD("Audio speed adjustments (p: %f) (i: %f)", proportionalAdjustment, integralAdjustment);

    return 1.0 - (finalAdjustment);
}

int32_t Audio::roundToEven(int32_t x) {
    return (x / 2) * 2;
}

void Audio::waitForWritableAudio(size_t frames) const {
    if (deviceAudioProfile != DeviceAudioProfile::PS202 || fifoBuffer == nullptr) {
        return;
    }

    const uint32_t requiredFrames = std::min(
        static_cast<uint32_t>(frames),
        fifoBuffer->getBufferCapacityInFrames()
    );

    while (
        startRequested &&
        streamStarted &&
        fifoBuffer->getBufferCapacityInFrames() - fifoBuffer->getFullFramesAvailable() < requiredFrames
    ) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}

void Audio::resetFifo() {
    if (fifoBuffer == nullptr) {
        return;
    }

    fifoBuffer->setReadCounter(0);
    fifoBuffer->setWriteCounter(0);
    framesToSubmit = 0.0;
    errorIntegral = 0.0;
}

void Audio::startStreamIfReady() {
    if (!startRequested || streamStarted || stream == nullptr || fifoBuffer == nullptr) {
        return;
    }

    if (!hasBufferedAudioForStart()) {
        return;
    }

    auto result = stream->requestStart();
    if (result == oboe::Result::OK) {
        streamStarted = true;
        LOGI(
            "Started audio stream with bufferedFrames=%u startThreshold=%u",
            fifoBuffer->getFullFramesAvailable(),
            static_cast<unsigned>(std::ceil(stream->getFramesPerBurst() * baseConversionFactor * getStartThresholdFactor()))
        );
    } else {
        LOGE("Failed to start audio stream. Error: %s", oboe::convertToText(result));
    }
}

bool Audio::hasBufferedAudioForStart() const {
    if (fifoBuffer == nullptr || stream == nullptr) {
        return false;
    }

    uint32_t startThresholdFrames = static_cast<uint32_t>(
        std::ceil(stream->getFramesPerBurst() * baseConversionFactor * getStartThresholdFactor())
    );
    return fifoBuffer->getFullFramesAvailable() >= std::max(1u, startThresholdFrames);
}

bool Audio::usesLegacyAudioPath() const {
    return deviceAudioProfile == DeviceAudioProfile::PS202 || !oboe::AudioStreamBuilder::isAAudioRecommended();
}

Audio::AudioLatencySettings Audio::getOpenSLESLatencySettings() const {
    switch (deviceAudioProfile) {
        case DeviceAudioProfile::PS202:
            return PS202_OPENSL_LATENCY_SETTINGS;
        case DeviceAudioProfile::DEFAULT:
        default:
            return DEFAULT_OPENSL_LATENCY_SETTINGS;
    }
}

int32_t Audio::getFramesPerCallback(int32_t audioBufferSize) const {
    if (deviceAudioProfile == DeviceAudioProfile::PS202) {
        return PS202_AUDIO_BLOCK_FRAMES;
    }

    return std::max(1, audioBufferSize / 10);
}

double Audio::getStartThresholdFactor() const {
    if (deviceAudioProfile == DeviceAudioProfile::PS202) {
        return 4.0;
    }

    if (usesLegacyAudioPath()) {
        return 3.0;
    }

    return 2.0;
}

bool Audio::shouldPreferLowLatencyAudio(bool preferLowLatencyAudio) const {
    if (usesLegacyAudioPath()) {
        return false;
    }
    return preferLowLatencyAudio;
}

bool Audio::usesDynamicBufferSync() const {
    return deviceAudioProfile != DeviceAudioProfile::PS202;
}

void Audio::onErrorAfterClose(oboe::AudioStream* oldStream, oboe::Result result) {
    AudioStreamErrorCallback::onErrorAfterClose(oldStream, result);
    LOGI("Stream error in oboe::onErrorAfterClose %s", oboe::convertToText(result));

    if (result != oboe::Result::ErrorDisconnected)
        return;

    initializeStream();
    if (startRequested) {
        startStreamIfReady();
    }
}

} //namespace libretrodroid
