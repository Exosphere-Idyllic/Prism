package com.example.prism.data.db.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test
    fun `empty returns null`() = assertNull(SearchQuery.toFtsMatch(""))

    @Test
    fun `too short returns null`() = assertNull(SearchQuery.toFtsMatch("r"))

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
    fun `keeps accents`() =
        assertEquals("canción*", SearchQuery.toFtsMatch("canción"))
}
