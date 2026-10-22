package com.minimalist.launcher.domain.stats

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One table to export: a header row and its rows. */
data class CsvTable(val name: String, val header: List<String>, val rows: List<List<Any?>>)

object Csv {

    /** Quotes a field when it holds a comma, quote or line break; doubles inner quotes. */
    fun field(value: Any?): String {
        val text = value?.toString() ?: ""
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    fun render(table: CsvTable): String = buildString {
        append(table.header.joinToString(",") { field(it) }).append("\r\n")
        table.rows.forEach { row -> append(row.joinToString(",") { field(it) }).append("\r\n") }
    }

    /** Writes every table as its own CSV file inside one zip. */
    fun writeZip(tables: List<CsvTable>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            tables.forEach { table ->
                zip.putNextEntry(ZipEntry("${table.name}.csv"))
                zip.write(render(table).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
