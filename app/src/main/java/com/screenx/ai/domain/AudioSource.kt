package com.screenx.ai.domain

enum class AudioSource {
    NONE, MICROPHONE, INTERNAL, BOTH;

    val usesMic: Boolean get() = this == MICROPHONE || this == BOTH
    val usesInternal: Boolean get() = this == INTERNAL || this == BOTH
}
