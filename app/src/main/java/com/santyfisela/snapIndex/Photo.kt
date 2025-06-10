package com.santyfisela.snapIndex

data class Photo(
    val uri: String,
    val name: String,
    val size: Long,
    val date: Long,
    val metadata: Map<String, String> = emptyMap(),
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Photo

        if (size != other.size) return false
        if (date != other.date) return false
        if (uri != other.uri) return false
        if (name != other.name) return false
        if (metadata != other.metadata) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = size.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}