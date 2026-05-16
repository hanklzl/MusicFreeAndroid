package com.hank.musicfree.core.util

private val SPLIT_REGEX = Regex(
    """\s*(?:[/&、,]|\sfeat\.?\s|\sft\.?\s|\swith\s)\s*""",
    RegexOption.IGNORE_CASE,
)

fun splitArtists(raw: String): List<String> =
    raw.split(SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }.distinct()
