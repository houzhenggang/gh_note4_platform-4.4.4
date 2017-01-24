// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "content/renderer/media/rtc_video_decoder_factory.h"

#include "base/location.h"
#include "base/memory/scoped_ptr.h"
#include "content/common/gpu/client/gpu_video_decode_accelerator_host.h"
#include "content/renderer/media/rtc_video_decoder.h"
#include "media/filters/gpu_video_accelerator_factories.h"

namespace content {

// Translate from media::VideoDecodeAccelerator::SupportedProfile to
// cricket::WebRtcVideoDecoderFactory::VideoCodec
cricket::WebRtcVideoDecoderFactory::VideoCodec VDAToWebRTCCodec(
    const media::VideoDecodeAccelerator::SupportedProfile& profile) {
  webrtc::VideoCodecType type = webrtc::kVideoCodecUnknown;
  std::string name;
  int width = 0, height = 0, fps = 0;

  if (profile.profile >= media::VP8PROFILE_MIN &&
      profile.profile <= media::VP8PROFILE_MAX) {
    type = webrtc::kVideoCodecVP8;
    name = "VP8";
  } else if (profile.profile >= media::H264PROFILE_MIN &&
             profile.profile <= media::H264PROFILE_MAX) {
#if defined(ENABLE_WEBRTC_H264_CODEC)
    type = webrtc::kVideoCodecH264;
    name = "H264";
#else
    type = webrtc::kVideoCodecGeneric;
    name = "CAST1";
#endif
  }

  if (type != webrtc::kVideoCodecUnknown) {
    width = profile.max_resolution.width();
    height = profile.max_resolution.height();
    fps = profile.max_framerate.numerator;
    DCHECK_EQ(profile.max_framerate.denominator, 1U);
  }

  return cricket::WebRtcVideoDecoderFactory::VideoCodec(
      type, name, width, height, fps);
}

RTCVideoDecoderFactory::RTCVideoDecoderFactory(
    const scoped_refptr<media::GpuVideoAcceleratorFactories>& gpu_factories)
    : gpu_factories_(gpu_factories) {
  DVLOG(2) << "RTCVideoDecoderFactory";
  // Query media::VideoDecodeAccelerator (statically) for our supported codecs.
  std::vector<media::VideoDecodeAccelerator::SupportedProfile> profiles =
      GpuVideoDecodeAcceleratorHost::GetSupportedProfiles();
  for (size_t i = 0; i < profiles.size(); ++i) {
    VideoCodec codec = VDAToWebRTCCodec(profiles[i]);
    if (codec.type != webrtc::kVideoCodecUnknown)
      codecs_.push_back(codec);
  }
}

RTCVideoDecoderFactory::~RTCVideoDecoderFactory() {
  DVLOG(2) << "~RTCVideoDecoderFactory";
}

webrtc::VideoDecoder* RTCVideoDecoderFactory::CreateVideoDecoder(
    webrtc::VideoCodecType type) {
  DVLOG(2) << "CreateVideoDecoder";
  bool found = false;
  for (size_t i = 0; i < codecs_.size(); ++i) {
    if (codecs_[i].type == type) {
      found = true;
      break;
    }
  }
  if (!found)
    return NULL;
  scoped_ptr<RTCVideoDecoder> decoder =
      RTCVideoDecoder::Create(type, gpu_factories_);
  return decoder.release();
}

void RTCVideoDecoderFactory::DestroyVideoDecoder(
    webrtc::VideoDecoder* decoder) {
  DVLOG(2) << "DestroyVideoDecoder";
  gpu_factories_->GetTaskRunner()->DeleteSoon(FROM_HERE, decoder);
}

}  // namespace content
