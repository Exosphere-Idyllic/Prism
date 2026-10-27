package com.example.prism.data.db.search

/**
 * Sanitiza queries de usuario para FTS4 MATCH.
 *
 * Caracteres con significado en FTS4 que hay que eliminar:
 *   " -> inicia frase exacta
 *   * -> wildcard de prefijo (lo añadimos nosotros al final)
 *   ( ) -> agrupación
 *   - -> negación
 *   ^ : -> operadores de columna
 *   \ -> delimitador en algunos tokenizadores; se elimina preventivamente
 *
 * Devuelve null si la query queda vacía o es demasiado corta.
 * Con minLength = 1 (default del repositorio), "r" produce "r*" y matchea
 * prefijos de un carácter — comportamiento Spotify-style.
 */
object SearchQuery {
    // Caracteres reservados de FTS4: " * ( ) - ^ : \
    // El guión se coloca al final para evitar ambigüedad de rango en la clase de caracteres.
    // \\\\ en una cadena Kotlin normal → \\ en la regex → match de un literal backslash.
    private val SPECIAL_CHARS = Regex("[\"*()^:\\\\-]")
    private val WHITESPACE = Regex("\\s+")

    fun toFtsMatch(raw: String, minLength: Int = 2): String? {
        val cleaned = raw.replace(SPECIAL_CHARS, " ").trim()
        if (cleaned.length < minLength) return null
        val terms = cleaned.split(WHITESPACE).filter { it.isNotBlank() }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "$it*" }
    }
}
