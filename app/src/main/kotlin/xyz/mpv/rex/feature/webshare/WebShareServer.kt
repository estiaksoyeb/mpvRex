package xyz.mpv.rex.feature.webshare

import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/**
 * Embedded NanoHTTPD server for sharing local files with nearby devices over Wi-Fi / Hotspot.
 * Supports partial content (HTTP 206) for video seeking and resumable downloads.
 */
class WebShareServer(
  port: Int,
  private val token: String,
  private val files: List<ShareableFile>,
  private val onTransferUpdate: ((activeCount: Int) -> Unit)? = null,
) : NanoHTTPD(port) {

  data class ShareableFile(
    val id: String,
    val file: File,
    val displayName: String,
    val durationFormatted: String? = null,
  )

  private val fileMap = files.associateBy { it.id }

  override fun serve(session: IHTTPSession): Response {
    val uri = session.uri
    val params = session.parameters
    val clientToken = params["token"]?.firstOrNull()

    // 1. Security token validation
    if (clientToken != token && uri != "/favicon.ico") {
      return newFixedLengthResponse(
        Response.Status.FORBIDDEN,
        MIME_PLAINTEXT,
        "Access Denied: Invalid or missing security token."
      )
    }

    return try {
      when {
        uri == "/" -> serveIndexPage()
        uri.startsWith("/download/") -> {
          val fileId = uri.removePrefix("/download/")
          serveFile(session, fileId, isAttachment = true)
        }
        uri.startsWith("/stream/") -> {
          val fileId = uri.removePrefix("/stream/")
          serveFile(session, fileId, isAttachment = false)
        }
        uri == "/download-all" -> serveZipArchive()
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
      }
    } catch (e: Exception) {
      newFixedLengthResponse(
        Response.Status.INTERNAL_ERROR,
        MIME_PLAINTEXT,
        "500 Internal Server Error: ${e.message}"
      )
    }
  }

  private fun serveIndexPage(): Response {
    val items = files.map { item ->
      WebShareHtmlTemplate.SharedFileItem(
        id = item.id,
        name = item.displayName,
        size = item.file.length(),
        formattedSize = formatSize(item.file.length()),
        durationFormatted = item.durationFormatted,
        mimeType = getMimeType(item.file),
      )
    }
    val html = WebShareHtmlTemplate.renderHtml(items, token)
    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
  }

  private fun serveFile(session: IHTTPSession, fileId: String, isAttachment: Boolean): Response {
    val shareable = fileMap[fileId] ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File not found")
    val file = shareable.file
    if (!file.exists() || !file.canRead()) {
      return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "File unreadable on device")
    }

    val fileLength = file.length()
    val mimeType = if (isAttachment) "application/octet-stream" else getMimeType(file)
    val headers = session.headers
    val rangeHeader = headers["range"]

    var startFrom: Long = 0
    var endAt: Long = fileLength - 1

    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
      val range = rangeHeader.removePrefix("bytes=").trim()
      val dashIdx = range.indexOf('-')
      if (dashIdx != -1) {
        val startStr = range.substring(0, dashIdx)
        val endStr = range.substring(dashIdx + 1)
        if (startStr.isNotEmpty()) {
          startFrom = startStr.toLongOrNull() ?: 0L
        }
        if (endStr.isNotEmpty()) {
          endAt = endStr.toLongOrNull() ?: (fileLength - 1)
        }
      }
    }

    if (startFrom >= fileLength) {
      val res = newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
      res.addHeader("Content-Range", "bytes */$fileLength")
      return res
    }

    endAt = endAt.coerceAtMost(fileLength - 1)
    val contentLength = endAt - startFrom + 1

    val fis = FileInputStream(file)
    if (startFrom > 0) {
      fis.skip(startFrom)
    }

    val status = if (rangeHeader != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK
    val response = newFixedLengthResponse(status, mimeType, fis, contentLength)

    response.addHeader("Accept-Ranges", "bytes")
    if (rangeHeader != null) {
      response.addHeader("Content-Range", "bytes $startFrom-$endAt/$fileLength")
    }

    if (isAttachment) {
      val encodedName = URLEncoder.encode(shareable.displayName, "UTF-8").replace("+", "%20")
      response.addHeader("Content-Disposition", "attachment; filename=\"${shareable.displayName.replace("\"", "")}\"; filename*=UTF-8''$encodedName")
    }

    return response
  }

  private fun serveZipArchive(): Response {
    val pipedOut = PipedOutputStream()
    val pipedIn = PipedInputStream(pipedOut, 64 * 1024)

    thread(name = "mpvRex-WebShare-ZipStream") {
      try {
        ZipOutputStream(pipedOut.buffered()).use { zipOut ->
          val buffer = ByteArray(64 * 1024)
          for (shareable in files) {
            val file = shareable.file
            if (file.exists() && file.canRead()) {
              val entry = ZipEntry(shareable.displayName)
              entry.size = file.length()
              entry.time = file.lastModified()
              zipOut.putNextEntry(entry)

              FileInputStream(file).use { fileIn ->
                var read: Int
                while (fileIn.read(buffer).also { read = it } != -1) {
                  zipOut.write(buffer, 0, read)
                }
              }
              zipOut.closeEntry()
            }
          }
          zipOut.flush()
        }
      } catch (e: Exception) {
        // Stream closed by receiver
      }
    }

    val response = newChunkedResponse(Response.Status.OK, "application/zip", pipedIn)
    response.addHeader("Content-Disposition", "attachment; filename=\"mpvRex_shared_files.zip\"")
    return response
  }

  private fun getMimeType(file: File): String {
    val ext = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "webm" -> "video/webm"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "flv" -> "video/x-flv"
      "ts" -> "video/mp2t"
      "mp3" -> "audio/mpeg"
      "aac" -> "audio/aac"
      "flac" -> "audio/flac"
      "ogg" -> "audio/ogg"
      else -> "video/*"
    }
  }

  private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
  }
}
