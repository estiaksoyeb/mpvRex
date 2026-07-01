package `is`.xyz.mpv

import kotlinx.coroutines.flow.Flow

object MPVLib {
    var activeMpv: MPV? = null

    private fun get(): MPV {
        return activeMpv ?: throw IllegalStateException("MPV is not initialized")
    }

    @JvmStatic
    fun command(vararg args: String) = get().command(*args)

    @JvmStatic
    fun commandNode(vararg args: String): MPVNode = get().commandNode(*args)

    @JvmStatic
    fun getPropertyInt(property: String): Int? = get().getPropertyInt(property)

    @JvmStatic
    fun setPropertyInt(property: String, value: Int) = get().setPropertyInt(property, value)

    @JvmStatic
    fun getPropertyDouble(property: String): Double? = get().getPropertyDouble(property)

    @JvmStatic
    fun setPropertyDouble(property: String, value: Double) = get().setPropertyDouble(property, value)

    @JvmStatic
    fun getPropertyBoolean(property: String): Boolean? = get().getPropertyBoolean(property)

    @JvmStatic
    fun setPropertyBoolean(property: String, value: Boolean) = get().setPropertyBoolean(property, value)

    @JvmStatic
    fun getPropertyString(property: String): String? = get().getPropertyString(property)

    @JvmStatic
    fun setPropertyString(property: String, value: String) = get().setPropertyString(property, value)

    @JvmStatic
    fun getPropertyNode(property: String): MPVNode = get().getPropertyNode(property)

    @JvmStatic
    fun setPropertyNode(property: String, value: MPVNode) = get().setPropertyNode(property, value)

    @JvmStatic
    fun getPropertyFloat(property: String): Float? = get().getPropertyFloat(property)

    @JvmStatic
    fun setPropertyFloat(property: String, value: Float) = get().setPropertyFloat(property, value)

    @JvmStatic
    fun getPropertyLong(property: String): Long? = get().getPropertyLong(property)

    @JvmStatic
    fun setPropertyLong(property: String, value: Long) = get().setPropertyLong(property, value)

    @JvmStatic
    fun observeProperty(property: String, format: Int) = get().observeProperty(property, format)

    @JvmStatic
    fun addObserver(observer: MPV.EventObserver) = get().addObserver(observer)

    @JvmStatic
    fun removeObserver(observer: MPV.EventObserver) = get().removeObserver(observer)

    @JvmStatic
    fun addLogObserver(observer: MPV.LogObserver) = get().addLogObserver(observer)

    @JvmStatic
    fun removeLogObserver(observer: MPV.LogObserver) = get().removeLogObserver(observer)

    @JvmStatic
    fun destroy() {
        activeMpv?.close()
        activeMpv = null
    }

    // Property Flow Accessors matching old API syntax
    val propBoolean = object {
        operator fun get(key: String): Flow<Boolean> = get().propFlow.getBooleanFlow(key)
    }

    val propInt = object {
        operator fun get(key: String): Flow<Int> = get().propFlow.getIntFlow(key)
    }

    val propString = object {
        operator fun get(key: String): Flow<String> = get().propFlow.getStringFlow(key)
    }

    val propDouble = object {
        operator fun get(key: String): Flow<Double> = get().propFlow.getDoubleFlow(key)
    }

    val propFloat = object {
        operator fun get(key: String): Flow<Float> = get().propFlow.getFloatFlow(key)
    }

    val propLong = object {
        operator fun get(key: String): Flow<Long> = get().propFlow.getLongFlow(key)
    }

    val propNode = object {
        operator fun get(key: String): Flow<MPVNode> = get().propFlow.getNodeFlow(key)
    }

    typealias EventObserver = MPV.EventObserver
    typealias LogObserver = MPV.LogObserver

    object MpvEvent {
        const val MPV_EVENT_NONE = MPV.mpvEvent.MPV_EVENT_NONE
        const val MPV_EVENT_SHUTDOWN = MPV.mpvEvent.MPV_EVENT_SHUTDOWN
        const val MPV_EVENT_LOG_MESSAGE = MPV.mpvEvent.MPV_EVENT_LOG_MESSAGE
        const val MPV_EVENT_GET_PROPERTY_REPLY = MPV.mpvEvent.MPV_EVENT_GET_PROPERTY_REPLY
        const val MPV_EVENT_SET_PROPERTY_REPLY = MPV.mpvEvent.MPV_EVENT_SET_PROPERTY_REPLY
        const val MPV_EVENT_COMMAND_REPLY = MPV.mpvEvent.MPV_EVENT_COMMAND_REPLY
        const val MPV_EVENT_START_FILE = MPV.mpvEvent.MPV_EVENT_START_FILE
        const val MPV_EVENT_END_FILE = MPV.mpvEvent.MPV_EVENT_END_FILE
        const val MPV_EVENT_FILE_LOADED = MPV.mpvEvent.MPV_EVENT_FILE_LOADED
        const val MPV_EVENT_CLIENT_MESSAGE = MPV.mpvEvent.MPV_EVENT_CLIENT_MESSAGE
        const val MPV_EVENT_VIDEO_RECONFIG = MPV.mpvEvent.MPV_EVENT_VIDEO_RECONFIG
        const val MPV_EVENT_AUDIO_RECONFIG = MPV.mpvEvent.MPV_EVENT_AUDIO_RECONFIG
        const val MPV_EVENT_SEEK = MPV.mpvEvent.MPV_EVENT_SEEK
        const val MPV_EVENT_PLAYBACK_RESTART = MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART
        const val MPV_EVENT_PROPERTY_CHANGE = MPV.mpvEvent.MPV_EVENT_PROPERTY_CHANGE
        const val MPV_EVENT_QUEUE_OVERFLOW = MPV.mpvEvent.MPV_EVENT_QUEUE_OVERFLOW
        const val MPV_EVENT_HOOK = MPV.mpvEvent.MPV_EVENT_HOOK
    }

    object MpvFormat {
        const val MPV_FORMAT_NONE = MPV.mpvFormat.MPV_FORMAT_NONE
        const val MPV_FORMAT_STRING = MPV.mpvFormat.MPV_FORMAT_STRING
        const val MPV_FORMAT_OSD_STRING = MPV.mpvFormat.MPV_FORMAT_OSD_STRING
        const val MPV_FORMAT_FLAG = MPV.mpvFormat.MPV_FORMAT_FLAG
        const val MPV_FORMAT_INT64 = MPV.mpvFormat.MPV_FORMAT_INT64
        const val MPV_FORMAT_DOUBLE = MPV.mpvFormat.MPV_FORMAT_DOUBLE
        const val MPV_FORMAT_NODE = MPV.mpvFormat.MPV_FORMAT_NODE
        const val MPV_FORMAT_NODE_ARRAY = MPV.mpvFormat.MPV_FORMAT_NODE_ARRAY
        const val MPV_FORMAT_NODE_MAP = MPV.mpvFormat.MPV_FORMAT_NODE_MAP
    }

    object MpvLogLevel {
        const val MPV_LOG_LEVEL_NONE = MPV.mpvLogLevel.MPV_LOG_LEVEL_NONE
        const val MPV_LOG_LEVEL_FATAL = MPV.mpvLogLevel.MPV_LOG_LEVEL_FATAL
        const val MPV_LOG_LEVEL_ERROR = MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR
        const val MPV_LOG_LEVEL_WARN = MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN
        const val MPV_LOG_LEVEL_INFO = MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO
        const val MPV_LOG_LEVEL_V = MPV.mpvLogLevel.MPV_LOG_LEVEL_V
        const val MPV_LOG_LEVEL_DEBUG = MPV.mpvLogLevel.MPV_LOG_LEVEL_DEBUG
        const val MPV_LOG_LEVEL_TRACE = MPV.mpvLogLevel.MPV_LOG_LEVEL_TRACE
    }
}
