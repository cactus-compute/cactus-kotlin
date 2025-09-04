package com.cactus

expect suspend fun initializeSpeechRecognition(modelFolder: String, spkModelFolder: String): Boolean
expect suspend fun requestSpeechPermissions(): Boolean
expect suspend fun performSpeechRecognition(params: SpeechRecognitionParams, sampleRate: Int): SpeechRecognitionResult?
expect fun stopSpeechRecognition()
expect fun isSpeechRecognitionAvailable(): Boolean
expect fun isSpeechRecognitionAuthorized(): Boolean