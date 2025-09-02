package com.cactus

expect suspend fun initializeSpeechRecognition(): Boolean
expect suspend fun requestSpeechPermissions(): Boolean
expect suspend fun performSpeechRecognition(params: SpeechRecognitionParams): SpeechRecognitionResult?
expect suspend fun recognizeSpeechFromFile(audioFilePath: String, params: SpeechRecognitionParams): SpeechRecognitionResult?
expect fun stopSpeechRecognition()
expect fun isSpeechRecognitionAvailable(): Boolean
expect fun isSpeechRecognitionAuthorized(): Boolean 