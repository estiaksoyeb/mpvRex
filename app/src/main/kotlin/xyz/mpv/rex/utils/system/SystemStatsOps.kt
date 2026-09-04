package xyz.mpv.rex.utils.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt

data class BatterySnapshot(
  val percentageText: String,
  val rateText: String,
  val wattsText: String,
  val tempText: String,
  val tempCelsius: Float?,
)

data class CustomStatsSnapshot(
  val fileName: String,
  val renderContext: String,
  val video: String,
  val audio: String,
  val batteryPercentText: String,
  val batteryRateText: String,
  val batteryWattsText: String,
  val batteryTempText: String,
  val hdrActive: String,
  val decoderEfficiencyText: String,
  val thermalStateText: String,
  val peakTempText: String,
  val tempRiseText: String,
)

object SystemStatsOps {

  private const val MEMORY_STATS_SAMPLE_INTERVAL_MS = 5_000L

  fun readBatterySnapshot(context: Context): BatterySnapshot {
    val batteryIntent = try {
      context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (_: Exception) {
      null
    }

    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percentage = if (level >= 0 && scale > 0) {
      ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    } else {
      null
    }

    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    val currentMicroAmps = listOf(
      batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
      batteryManager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
    ).firstOrNull { value ->
      value != null && value != Long.MIN_VALUE && value != 0L
    }

    val voltageMilliVolts = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }

    val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
      ?: BatteryManager.BATTERY_STATUS_UNKNOWN
    val statusText = when (status) {
      BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
      BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
      BatteryManager.BATTERY_STATUS_FULL -> "Full"
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
      else -> when {
        (currentMicroAmps ?: 0L) > 0L -> "Charging"
        (currentMicroAmps ?: 0L) < 0L -> "Discharging"
        else -> "Unknown"
      }
    }

    val currentMilliAmps = currentMicroAmps?.let { abs(it).toFloat() / 1000f }?.takeIf { it > 0f }
    val rateText = if (currentMilliAmps != null && statusText != "Full" && statusText != "Unknown") {
      val formattedCurrent = if (currentMilliAmps >= 100f) {
        String.format("%.0f mA", currentMilliAmps)
      } else {
        String.format("%.1f mA", currentMilliAmps)
      }
      "$statusText $formattedCurrent"
    } else {
      statusText
    }

    val wattsText = if (currentMilliAmps != null && voltageMilliVolts != null && voltageMilliVolts > 0) {
      val watts = (currentMilliAmps / 1000f) * (voltageMilliVolts / 1000f)
      String.format("%.2f W", watts)
    } else {
      "-- W"
    }

    val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.takeIf { it > 0 }
    val tempCelsius = rawTemp?.let { it / 10f }
    val tempText = if (tempCelsius != null) {
      String.format("%.1f°C", tempCelsius)
    } else {
      "--°C"
    }

    return BatterySnapshot(
      percentageText = percentage?.let { "$it%" } ?: "--%",
      rateText = rateText,
      wattsText = wattsText,
      tempText = tempText,
      tempCelsius = tempCelsius,
    )
  }

  fun getCustomStatsFlow(
    context: Context,
  ): Flow<CustomStatsSnapshot> = flow {
    var startBatteryTemp: Float? = null
    var peakBatteryTemp = 0.0f

    while (coroutineContext.isActive) {
      val fileName = runCatching { MPVLib.getPropertyString("media-title") ?: "--" }.getOrDefault("--")
      val currentVideoOutput = runCatching {
        MPVLib.getPropertyString("current-vo")
          ?: MPVLib.getPropertyString("vo")
          ?: "--"
      }.getOrDefault("--")
      val videoCodec = runCatching { MPVLib.getPropertyString("video-codec") ?: "--" }.getOrDefault("--")
      val audioCodec = runCatching { MPVLib.getPropertyString("audio-codec-name") ?: "--" }.getOrDefault("--")

      val battery = readBatterySnapshot(context)
      val isPaused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrDefault(false) == true

      val currentTemp = battery.tempCelsius ?: 0f
      if (startBatteryTemp == null && currentTemp > 0f) {
        startBatteryTemp = currentTemp
      }
      if (currentTemp > peakBatteryTemp) {
        peakBatteryTemp = currentTemp
      }

      val peakTempText = if (peakBatteryTemp > 0f) String.format("%.1f°C", peakBatteryTemp) else "--°C"
      val tempRiseText = if (startBatteryTemp != null) {
        val rise = currentTemp - startBatteryTemp
        String.format("%+.1f°C", rise)
      } else {
        "+0.0°C"
      }

      val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        powerManager?.currentThermalStatus ?: 0
      } else {
        0
      }
      val thermalStateText = when (thermalStatus) {
        0 -> "Normal"
        1 -> "Light Throttling"
        2 -> "Moderate Throttling"
        3 -> "Severe Throttling"
        4 -> "Critical Throttling"
        5 -> "Emergency!"
        6 -> "Overheating Shutdown!"
        else -> "Normal"
      }

      val currentHwdec = runCatching { MPVLib.getPropertyString("hwdec-current") ?: "no" }.getOrDefault("no")
      val gpuApi = runCatching { MPVLib.getPropertyString("gpu-api") ?: "--" }.getOrDefault("--")
      val gpuContext = runCatching { MPVLib.getPropertyString("gpu-context") ?: "--" }.getOrDefault("--")
      val renderContext = "$currentVideoOutput | $gpuApi | $gpuContext"
      val decoderEfficiencyText = when {
        currentHwdec == "no" || currentHwdec.isBlank() -> "Low (Software Decoding, CPU-heavy)"
        currentHwdec.contains("copy") -> "Moderate (Hardware-copy, GPU texture overhead)"
        else -> "High (Hardware Direct, $gpuApi backend)"
      }

      val hdrActive = runCatching {
        val sourceGamma = MPVLib.getPropertyString("video-params/gamma").orEmpty()
        val sourcePrimaries = MPVLib.getPropertyString("video-params/primaries").orEmpty()
        val sourcePeak = MPVLib.getPropertyDouble("video-params/sig-peak") ?: 0.0

        val isHdrSource = sourceGamma == "pq" ||
          sourceGamma == "hlg" ||
          (sourcePrimaries == "bt.2020" && sourcePeak > 1.0)

        if (isHdrSource) "HDR Source" else "SDR Source"
      }.getOrDefault("Unknown")

      emit(
        CustomStatsSnapshot(
          fileName = fileName,
          renderContext = renderContext,
          video = videoCodec,
          audio = audioCodec,
          batteryPercentText = battery.percentageText,
          batteryRateText = battery.rateText,
          batteryWattsText = battery.wattsText,
          batteryTempText = battery.tempText,
          hdrActive = hdrActive,
          decoderEfficiencyText = decoderEfficiencyText,
          thermalStateText = thermalStateText,
          peakTempText = peakTempText,
          tempRiseText = tempRiseText,
        )
      )

      delay(if (isPaused) 2000L else 1000L)
    }
  }.flowOn(Dispatchers.IO)
}
