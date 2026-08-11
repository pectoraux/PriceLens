package com.pricelens.ml.retrieval.hnsw

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*
import kotlin.math.sqrt

/**
 * A memory-mapped HNSW index reader.
 * Designed for INT8 quantized 512-d embeddings.
 */
class HnswIndex(file: File) : AutoCloseable {

    private val raf = RandomAccessFile(file, "r")
    private val buffer: ByteBuffer = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
        .order(ByteOrder.LITTLE_ENDIAN)

    private val dimension: Int
    private val maxLevel: Int
    private val entryNodeId: Int
    private val nodeCount: Int
    private val offsetTablePos: Int

    data class Neighbor(val id: Int, val distance: Float, val label: String)

    init {
        // Simple header validation
        val magic = buffer.getInt()
        if (magic != 0x484E5357) { // "HNSW"
            throw IllegalStateException("Invalid HNSW index file: magic mismatch")
        }
        buffer.getInt() // version
        dimension = buffer.getInt()
        buffer.getInt() // M
        buffer.getInt() // efConstruction
        maxLevel = buffer.getInt()
        entryNodeId = buffer.getInt()
        nodeCount = buffer.getInt()
        offsetTablePos = 32
    }

    /**
     * Searches for the k nearest neighbors of the query embedding.
     */
    fun search(query: FloatArray, k: Int, ef: Int = 64): List<Neighbor> {
        if (nodeCount == 0) return emptyList()

        // 1. Quantize query to INT8 (simplified)
        val qInt8 = ByteArray(query.size) { i ->
            (query[i] * 127).toInt().coerceIn(-128, 127).toByte()
        }

        var currNode = entryNodeId
        var currDist = distance(qInt8, currNode)

        // 2. Greedy search through upper levels
        for (lc in maxLevel downTo 1) {
            var changed = true
            while (changed) {
                changed = false
                val neighbors = getNeighbors(currNode, lc)
                for (neighborId in neighbors) {
                    val d = distance(qInt8, neighborId)
                    if (d < currDist) {
                        currDist = d
                        currNode = neighborId
                        changed = true
                    }
                }
            }
        }

        // 3. Search on level 0 with priority queue
        return searchLevel0(qInt8, currNode, k, ef)
    }

    private fun searchLevel0(query: ByteArray, entryId: Int, k: Int, ef: Int): List<Neighbor> {
        val topCandidates = PriorityQueue<Neighbor>(compareByDescending { it.distance })
        val candidateQueue = PriorityQueue<Neighbor>(compareBy { it.distance })
        val visited = mutableSetOf<Int>()

        val d0 = distance(query, entryId)
        val n0 = Neighbor(entryId, d0, getLabel(entryId))
        topCandidates.add(n0)
        candidateQueue.add(n0)
        visited.add(entryId)

        while (candidateQueue.isNotEmpty()) {
            val c = candidateQueue.poll()!!
            val f = topCandidates.peek()!!
            
            if (c.distance > f.distance) break

            val neighbors = getNeighbors(c.id, 0)
            for (neighborId in neighbors) {
                if (neighborId !in visited) {
                    visited.add(neighborId)
                    val d = distance(query, neighborId)
                    if (d < f.distance || topCandidates.size < ef) {
                        val n = Neighbor(neighborId, d, getLabel(neighborId))
                        candidateQueue.add(n)
                        topCandidates.add(n)
                        if (topCandidates.size > ef) {
                            topCandidates.poll()
                        }
                    }
                }
            }
        }

        return topCandidates.toList().sortedBy { it.distance }.take(k)
    }

    /**
     * Dummy distance function: L2 on INT8
     */
    private fun distance(q: ByteArray, nodeId: Int): Float {
        val vectorOffset = getNodeOffset(nodeId)
        var sum = 0.0f
        for (i in 0 until dimension) {
            val v = buffer.get(vectorOffset + i).toFloat()
            val diff = q[i] - v
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun getNodeOffset(nodeId: Int): Int {
        return buffer.getInt(offsetTablePos + nodeId * 4)
    }

    private fun getNeighbors(nodeId: Int, level: Int): IntArray {
        var pos = getNodeOffset(nodeId)
        pos += dimension // Skip vector
        val labelLen = buffer.getInt(pos)
        pos += 4 + labelLen // Skip label
        val nodeMaxLevel = buffer.getInt(pos)
        pos += 4
        
        if (level > nodeMaxLevel) return intArrayOf()
        
        // Skip levels higher than the target level
        for (l in nodeMaxLevel downTo level + 1) {
            val count = buffer.getInt(pos)
            pos += 4 + count * 4
        }
        
        val count = buffer.getInt(pos)
        val neighbors = IntArray(count)
        for (i in 0 until count) {
            neighbors[i] = buffer.getInt(pos + 4 + i * 4)
        }
        return neighbors
    }

    private fun getLabel(nodeId: Int): String {
        var pos = getNodeOffset(nodeId)
        pos += dimension // Skip vector
        val labelLen = buffer.getInt(pos)
        val bytes = ByteArray(labelLen)
        val originalPos = buffer.position()
        buffer.position(pos + 4)
        buffer.get(bytes)
        buffer.position(originalPos)
        return String(bytes, Charsets.UTF_8)
    }

    override fun close() {
        raf.close()
    }
}
