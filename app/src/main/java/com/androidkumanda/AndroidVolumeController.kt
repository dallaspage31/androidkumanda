package com.androidkumanda

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

interface VolumePlatform {
    fun isVolumeFixed(): Boolean
    fun musicVolumeIndex(): Int
    fun adjustMusic(direction: VolumeDirection)
    fun connectedOutputTypes(): Set<Int>? = null
}

class AndroidVolumeController(private val platform: VolumePlatform) {
    constructor(context: Context) : this(AndroidVolumePlatform(context))

    fun isVolumeFixed(): Boolean = platform.isVolumeFixed()

    fun adjust(direction: VolumeDirection): VolumeAdjustmentResult {
        if (platform.isVolumeFixed()) return VolumeAdjustmentResult.FIXED_POLICY
        val before = platform.musicVolumeIndex()
        platform.adjustMusic(direction)
        return if (platform.musicVolumeIndex() != before) {
            VolumeAdjustmentResult.INDEX_CHANGED
        } else {
            VolumeAdjustmentResult.NO_INDEX_CHANGE
        }
    }

    fun connectedOutputTypes(): Set<Int>? = platform.connectedOutputTypes()

    companion object {
        fun outputTypeName(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_AUX_LINE -> "AUX line"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Dahili hoparlör"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog line"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital line"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB audio"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            else -> "Unknown output type ($type)"
        }
    }
}

private class AndroidVolumePlatform(context: Context) : VolumePlatform {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun isVolumeFixed(): Boolean = audioManager.isVolumeFixed

    override fun musicVolumeIndex(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun adjustMusic(direction: VolumeDirection) {
        val adjustment = when (direction) {
            VolumeDirection.RAISE -> AudioManager.ADJUST_RAISE
            VolumeDirection.LOWER -> AudioManager.ADJUST_LOWER
        }
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            adjustment,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun connectedOutputTypes(): Set<Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map(AudioDeviceInfo::getType)
            .toSet()
    }
}
