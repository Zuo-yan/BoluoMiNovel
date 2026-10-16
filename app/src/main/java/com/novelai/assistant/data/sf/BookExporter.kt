package com.novelai.assistant.data.sf

import com.novelai.assistant.data.db.BookEntity
import com.novelai.assistant.data.db.ChapterEntity
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class BookExportFormat(val extension: String, val mime: String) {
    TXT("txt", "text/plain"), MARKDOWN("md", "text/markdown"), EPUB("epub", "application/epub+zip")
}

object BookExporter {
    fun write(out: OutputStream, book: BookEntity, selected: List<ChapterEntity>, format: BookExportFormat, allowPartial: Boolean) {
        require(selected.isNotEmpty()) { "没有可导出的章节" }
        val ordered = selected.sortedBy { it.chapterIndex }
        val missing = ordered.filter { it.content.isBlank() }
        require(allowPartial || missing.isEmpty()) { "存在缺章，请先确认是否导出不完整版本" }
        val ready = ordered.filter { it.content.isNotBlank() }
        require(ready.isNotEmpty()) { "没有已取得正文的章节，请先下载" }
        val notice = if (missing.isEmpty()) "" else "不完整版本：缺少 ${missing.size} 章\n" + missing.joinToString("\n") { "${it.chapterIndex + 1}. ${it.title}：${it.fetchError.ifBlank { "未下载" }}" }
        if (format == BookExportFormat.EPUB) {
            epub(out, book, ready, notice)
        } else {
            val heading = if (format == BookExportFormat.MARKDOWN) "# " else ""
            out.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.appendLine("$heading${book.title}").appendLine(book.author).appendLine()
                if (notice.isNotBlank()) writer.appendLine(notice).appendLine()
                ready.forEach { writer.appendLine("${if (heading.isNotEmpty()) "## " else ""}${it.title}").appendLine().appendLine(it.content).appendLine() }
            }
        }
    }

    private fun xml(value: String) = value.filter { it == '\n' || it == '\t' || it == '\r' || it.code >= 32 }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private fun epub(out: OutputStream, book: BookEntity, chapters: List<ChapterEntity>, notice: String) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String, stored: Boolean = false) {
                val bytes = text.toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry(name).apply {
                    if (stored) { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; crc = CRC32().apply { update(bytes) }.value }
                })
                zip.write(bytes); zip.closeEntry()
            }
            fun page(title: String, body: String) = """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN"><head><title>${xml(title)}</title><meta charset="UTF-8"/></head><body><h1>${xml(title)}</h1>$body</body></html>"""
            entry("mimetype", "application/epub+zip", true)
            entry("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            val items = mutableListOf("<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>")
            val spine = mutableListOf<String>()
            val nav = mutableListOf<String>()
            if (notice.isNotBlank()) {
                entry("OEBPS/notice.xhtml", page("下载缺章说明", "<p>${xml(notice).replace("\n", "</p><p>")}</p>"))
                items += "<item id=\"notice\" href=\"notice.xhtml\" media-type=\"application/xhtml+xml\"/>"
                spine += "<itemref idref=\"notice\"/>"
                nav += "<li><a href=\"notice.xhtml\">下载缺章说明</a></li>"
            }
            chapters.forEachIndexed { i, chapter ->
                val name = "chapter$i.xhtml"
                entry("OEBPS/$name", page(chapter.title, chapter.content.lines().filter { it.isNotBlank() }.joinToString("") { "<p>${xml(it)}</p>" }))
                items += "<item id=\"c$i\" href=\"$name\" media-type=\"application/xhtml+xml\"/>"
                spine += "<itemref idref=\"c$i\"/>"
                nav += "<li><a href=\"$name\">${xml(chapter.title)}</a></li>"
            }
            entry("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN"><head><title>目录</title></head><body><nav epub:type="toc"><h1>目录</h1><ol>${nav.joinToString("")}</ol></nav></body></html>""")
            entry("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">${xml(book.id)}</dc:identifier><dc:title>${xml(book.title)}</dc:title><dc:creator>${xml(book.author.ifBlank { "未知作者" })}</dc:creator><dc:language>zh-CN</dc:language><meta property="dcterms:modified">${java.time.Instant.now().toString().substringBefore('.').substringBefore('Z')}Z</meta></metadata><manifest>${items.joinToString("")}</manifest><spine>${spine.joinToString("")}</spine></package>""")
        }
    }
}
