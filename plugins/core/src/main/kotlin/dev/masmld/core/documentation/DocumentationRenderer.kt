package dev.masmld.core.documentation

object DocumentationRenderer {
    private fun escape(text: String) = buildString {
        for (char in text) append(when (char) {
            '&' -> "&amp;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            else -> char.toString()
        })
    }
    fun render(entry: DocumentationEntry): String = buildString {
        append("<div class='definition'><pre>${escape(entry.syntax)}</pre></div>")
        append("<div class='content'><p><b>${escape(entry.kind.label)}</b> · ${escape(entry.applicability)}</p>")
        append("<p>${escape(entry.summary)}</p>")
        if (entry.notes.isNotEmpty()) append("<p>${escape(entry.notes)}</p>")
        if (entry.example.isNotEmpty()) append("<p><b>示例</b></p><pre>${escape(entry.example)}</pre>")
        append("</div><div class='bottom'><b>资料来源</b><ul>")
        for (source in entry.sources) {
            // Catalog links may only navigate to external HTTPS documentation.
            val safeLink = runCatching {
                val uri = java.net.URI(source.url)
                uri.scheme == "https" && uri.host != null && uri.userInfo == null
            }.getOrDefault(false)
            append("<li>")
            if (safeLink) append("<a href=\"${escape(source.url)}\">${escape(source.title)}</a>")
            else append(escape(source.title))
            append(" — ${escape(source.revision)}; ${escape(source.notice)}</li>")
        }
        append("</ul></div>")
    }
}
