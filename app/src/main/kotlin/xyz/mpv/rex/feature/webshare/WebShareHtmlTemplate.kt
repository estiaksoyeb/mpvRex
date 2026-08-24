package xyz.mpv.rex.feature.webshare

import java.text.DecimalFormat

object WebShareHtmlTemplate {

  data class SharedFileItem(
    val id: String,
    val name: String,
    val size: Long,
    val formattedSize: String,
    val durationFormatted: String?,
    val mimeType: String,
  )

  fun renderHtml(
    files: List<SharedFileItem>,
    token: String? = null,
    serverTitle: String = "mpvRex Web Share",
  ): String {
    val totalSize = files.sumOf { it.size }
    val totalSizeFormatted = formatFileSize(totalSize)
    val fileCount = files.size
    val multipleFiles = fileCount > 1
    val querySuffix = if (!token.isNullOrEmpty()) "?t=$token" else ""

    val fileCards = buildString {
      files.forEachIndexed { index, file ->
        val safeName = escapeHtml(file.name)
        val downloadUrl = "/download/${file.id}$querySuffix"
        val streamUrl = "/stream/${file.id}$querySuffix"

        append("""
          <div class="card">
            <div class="card-icon">
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="5 3 19 12 5 21 5 3"></polygon>
              </svg>
            </div>
            <div class="card-content">
              <div class="card-title" title="$safeName">$safeName</div>
              <div class="card-meta">
                <span>${file.formattedSize}</span>
                ${if (file.durationFormatted != null) """<span class="dot">•</span><span>${file.durationFormatted}</span>""" else ""}
              </div>
            </div>
            <div class="card-actions">
              <a href="$downloadUrl" class="btn btn-primary" download="$safeName">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="7 10 12 15 17 10"></polyline>
                  <line x1="12" y1="15" x2="12" y2="3"></line>
                </svg>
                Download
              </a>
              <button class="btn btn-secondary" onclick="playMedia('$streamUrl', '$safeName')">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <polygon points="10 8 16 12 10 16 10 8"></polygon>
                </svg>
                Play
              </button>
            </div>
          </div>
        """.trimIndent())
      }
    }

    return """
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>$serverTitle</title>
        <style>
          :root {
            --bg-color: #0f141c;
            --surface-color: #19202c;
            --surface-hover: #222b3a;
            --primary-color: #3b82f6;
            --primary-hover: #2563eb;
            --secondary-bg: #2d3748;
            --secondary-hover: #374151;
            --text-main: #f3f4f6;
            --text-muted: #9ca3af;
            --border-color: #2e384d;
            --radius-lg: 16px;
            --radius-md: 10px;
            --shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
          }

          @media (prefers-color-scheme: light) {
            :root {
              --bg-color: #f3f4f6;
              --surface-color: #ffffff;
              --surface-hover: #f9fafb;
              --primary-color: #2563eb;
              --primary-hover: #1d4ed8;
              --secondary-bg: #e5e7eb;
              --secondary-hover: #d1d5db;
              --text-main: #111827;
              --text-muted: #6b7280;
              --border-color: #e5e7eb;
              --shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
            }
          }

          * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          }

          body {
            background-color: var(--bg-color);
            color: var(--text-main);
            min-height: 100vh;
            padding: 24px 16px 48px;
            display: flex;
            justify-content: center;
          }

          .container {
            width: 100%;
            max-width: 680px;
          }

          .header {
            text-align: center;
            margin-bottom: 28px;
          }

          .app-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background-color: var(--surface-color);
            border: 1px solid var(--border-color);
            padding: 6px 14px;
            border-radius: 9999px;
            font-size: 13px;
            font-weight: 600;
            color: var(--primary-color);
            margin-bottom: 12px;
          }

          .title {
            font-size: 26px;
            font-weight: 800;
            letter-spacing: -0.5px;
            margin-bottom: 6px;
          }

          .subtitle {
            font-size: 14px;
            color: var(--text-muted);
          }

          .bulk-action {
            margin-bottom: 20px;
            text-align: right;
          }

          .card-list {
            display: flex;
            flex-direction: column;
            gap: 12px;
          }

          .card {
            background-color: var(--surface-color);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            padding: 16px;
            display: flex;
            align-items: center;
            gap: 16px;
            box-shadow: var(--shadow);
            transition: transform 0.15s ease, background-color 0.15s ease;
          }

          .card:hover {
            background-color: var(--surface-hover);
          }

          .card-icon {
            width: 48px;
            height: 48px;
            border-radius: var(--radius-md);
            background-color: rgba(59, 130, 246, 0.15);
            color: var(--primary-color);
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;
          }

          .card-content {
            flex: 1;
            min-width: 0;
          }

          .card-title {
            font-size: 15px;
            font-weight: 600;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-bottom: 4px;
          }

          .card-meta {
            font-size: 13px;
            color: var(--text-muted);
            display: flex;
            align-items: center;
            gap: 6px;
          }

          .dot {
            font-size: 10px;
          }

          .card-actions {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-shrink: 0;
          }

          .btn {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 8px 14px;
            border-radius: var(--radius-md);
            font-size: 13px;
            font-weight: 600;
            text-decoration: none;
            border: none;
            cursor: pointer;
            transition: background-color 0.15s ease, transform 0.1s ease;
          }

          .btn:active {
            transform: scale(0.97);
          }

          .btn-primary {
            background-color: var(--primary-color);
            color: #ffffff;
          }

          .btn-primary:hover {
            background-color: var(--primary-hover);
          }

          .btn-secondary {
            background-color: var(--secondary-bg);
            color: var(--text-main);
          }

          .btn-secondary:hover {
            background-color: var(--secondary-hover);
          }

          /* Video Modal */
          .modal-backdrop {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100vw;
            height: 100vh;
            background: rgba(0, 0, 0, 0.85);
            backdrop-filter: blur(8px);
            z-index: 1000;
            align-items: center;
            justify-content: center;
            padding: 16px;
          }

          .modal-content {
            background: var(--surface-color);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            width: 100%;
            max-width: 800px;
            overflow: hidden;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6);
          }

          .modal-header {
            padding: 12px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            border-bottom: 1px solid var(--border-color);
          }

          .modal-title {
            font-size: 15px;
            font-weight: 600;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            padding-right: 12px;
          }

          .modal-close {
            background: transparent;
            border: none;
            color: var(--text-muted);
            cursor: pointer;
            font-size: 24px;
            line-height: 1;
            padding: 4px;
          }

          .modal-close:hover {
            color: var(--text-main);
          }

          .modal-body {
            position: relative;
            background: #000;
            width: 100%;
            aspect-ratio: 16 / 9;
            display: flex;
            align-items: center;
            justify-content: center;
          }

          video {
            width: 100%;
            height: 100%;
          }

          @media (max-width: 480px) {
            .card {
              flex-direction: column;
              align-items: flex-start;
              gap: 12px;
            }
            .card-actions {
              width: 100%;
              justify-content: flex-end;
            }
            .btn {
              flex: 1;
              justify-content: center;
            }
          }
        </style>
      </head>
      <body>
        <div class="container">
          <header class="header">
            <div class="app-badge">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polygon points="10 8 16 12 10 16 10 8"></polygon>
              </svg>
              mpvRex Share
            </div>
            <h1 class="title">Shared Files</h1>
            <p class="subtitle">$fileCount ${if (multipleFiles) "files" else "file"} • $totalSizeFormatted total</p>
          </header>

          ${if (multipleFiles) """
            <div class="bulk-action">
              <a href="/download-all$querySuffix" class="btn btn-primary" download="mpvRex_shared_files.zip">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="21 8 21 21 3 21 3 8"></polyline>
                  <rect x="1" y="3" width="22" height="5"></rect>
                  <line x1="10" y1="12" x2="14" y2="12"></line>
                </svg>
                Download All (ZIP)
              </a>
            </div>
          """ else ""}

          <main class="card-list">
            $fileCards
          </main>
        </div>

        <!-- Media Player Modal -->
        <div id="mediaModal" class="modal-backdrop" onclick="if(event.target === this) closePlayer()">
          <div class="modal-content">
            <div class="modal-header">
              <div id="modalTitle" class="modal-title"></div>
              <button class="modal-close" onclick="closePlayer()">&times;</button>
            </div>
            <div class="modal-body">
              <video id="mediaPlayer" controls autoplay playsinline preload="metadata"></video>
            </div>
          </div>
        </div>

        <script>
          function playMedia(streamUrl, title) {
            const modal = document.getElementById('mediaModal');
            const player = document.getElementById('mediaPlayer');
            const titleEl = document.getElementById('modalTitle');
            titleEl.textContent = title;
            player.src = streamUrl;
            modal.style.display = 'flex';
            player.play().catch(() => {});
          }

          function closePlayer() {
            const modal = document.getElementById('mediaModal');
            const player = document.getElementById('mediaPlayer');
            player.pause();
            player.removeAttribute('src');
            player.load();
            modal.style.display = 'none';
          }

          document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closePlayer();
          });
        </script>
      </body>
      </html>
    """.trimIndent()
  }

  private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
  }

  private fun escapeHtml(text: String): String {
    return text.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }
}
