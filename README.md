# Encode Raw Audio
Shows how to use the MediaCodec and MediaMuxer to create an mp4 file from PCM audio

This shows how to:
1. Record raw PCM from the microphone via [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)
2. Encode it to AAC via [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
3. Mux it into an mp4 container via [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer)

## Note:  
For most use cases it is a lot easier to just use [MediaRecorder](https://developer.android.com/reference/android/media/MediaRecorder).  This is only useful if you have externally generated PCM audio.  This is most commonly seen in wav files.
