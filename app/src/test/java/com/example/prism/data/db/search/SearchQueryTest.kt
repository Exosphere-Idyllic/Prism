package com.example.prism.data.db.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    // --- Comportamiento por defecto (minLength = 2, valor del parámetro) ---

    @Test
    fun `empty returns null`() = assertNull(SearchQuery.toFtsMatch(""))

    @Test
    fun `single char with default minLength returns null`() =
        assertNull(SearchQuery.toFtsMatch("r"))

    @Test
    fun `simple adds wildcard`() =
        assertEquals("rock*", SearchQuery.toFtsMatch("rock"))

    @Test
    fun `multi word adds wildcard per term`() =
        assertEquals("rock* ballads*", SearchQuery.toFtsMatch("rock ballads"))

    @Test
    fun `strips quote`() =
        assertEquals("rock*", SearchQuery.toFtsMatch("\"rock\""))

    @Test
    fun `strips fts operators preserving terms`() =
        assertEquals("rock* foo*", SearchQuery.toFtsMatch("-rock* (foo)"))

    @Test
    fun `strips fts operators without extra terms`() =
        assertEquals("rock*", SearchQuery.toFtsMatch("-rock* ()"))

    @Test
    fun `strips backslash`() =
        assertEquals("rock*", SearchQuery.toFtsMatch("\\rock"))

    @Test
    fun `keeps accents`() =
        assertEquals("canción*", SearchQuery.toFtsMatch("canción"))

    // --- Opción C (minLength = 1): comportamiento Spotify-style ---

    @Test
    fun `one char with minLength 1`() =
        assertEquals("a*", SearchQuery.toFtsMatch("a", minLength = 1))

    @Test
    fun `single char with minLength 1 adds wildcard`() =
        assertEquals("r*", SearchQuery.toFtsMatch("r", minLength = 1))

    @Test
    fun `empty still returns null with minLength 1`() =
        assertNull(SearchQuery.toFtsMatch("", minLength = 1))

    @Test
    fun `single special char stripped to empty returns null with minLength 1`() =
        assertNull(SearchQuery.toFtsMatch("\"", minLength = 1))
}
