package com.pricelens.ml.retrieval.hnsw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HnswIndexTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `search should find neighbors in a synthetic index`() {
        val indexFile = tempFolder.newFile("test.hnsw")
        createSyntheticIndex(indexFile)

        val index = HnswIndex(indexFile)
        val query = FloatArray(512) { 0.5f }
        val neighbors = index.search(query, k = 1)

        assertTrue(neighbors.isNotEmpty())
        assertEquals("item.unknown", neighbors[0].label)
    }

    private fun createSyntheticIndex(file: File) {
        val raf = java.io.RandomAccessFile(file, "rw")
        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.putInt(0x484E5357) // MAGIC
        buffer.putInt(1) // Version
        buffer.putInt(512) // Dimension
        buffer.putInt(16) // M
        buffer.putInt(200) // ef
        buffer.putInt(0) // maxLevel
        buffer.putInt(0) // entryNodeId
        buffer.putInt(1) // nodeCount

        // Node 0
        buffer.putInt(0) // ID
        // Vector (INT8)
        repeat(512) { buffer.put(64.toByte()) }
        // Connection list for level 0 (Dummy)
        buffer.putInt(0) 

        raf.write(buffer.array(), 0, buffer.position())
        raf.close()
    }
}
