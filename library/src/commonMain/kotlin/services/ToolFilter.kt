package com.cactus.services

import com.cactus.CactusLM
import com.cactus.models.CactusTool
import kotlin.math.sqrt

enum class ToolFilterStrategy {
    /** Simple keyword matching with fuzzy matching and scoring (default, fast) */
    SIMPLE,
    /** Semantic search using embeddings for understanding intent (slower but more accurate) */
    SEMANTIC
}

data class ToolFilterConfig(
    val strategy: ToolFilterStrategy = ToolFilterStrategy.SIMPLE,
    val maxTools: Int? = null,
    val similarityThreshold: Double = 0.3
) {
    companion object {
        fun simple(maxTools: Int = 3): ToolFilterConfig {
            return ToolFilterConfig(
                strategy = ToolFilterStrategy.SIMPLE,
                maxTools = maxTools,
                similarityThreshold = 0.3
            )
        }
    }
}

class ToolFilterService(
    private val config: ToolFilterConfig = ToolFilterConfig(),
    private val lm: CactusLM
) {
    suspend fun filterTools(query: String, tools: List<CactusTool>): List<CactusTool> {
        if (tools.isEmpty()) return tools
        
        return when (config.strategy) {
            ToolFilterStrategy.SIMPLE -> filterByEnhancedKeyword(query, tools)
            ToolFilterStrategy.SEMANTIC -> filterBySemantic(query, tools)
        }
    }

    private fun filterByEnhancedKeyword(query: String, tools: List<CactusTool>): List<CactusTool> {
        val queryLower = query.lowercase()
        val queryWords = extractKeywords(queryLower)
        
        if (queryWords.isEmpty()) {
            return applyMaxToolsLimit(tools)
        }
        
        val scoredTools = tools.map { tool ->
            val score = calculateToolScore(tool, queryWords)
            ScoredTool(tool, score)
        }
        
        val sortedTools = scoredTools.sortedByDescending { it.score }
        
        val filteredTools = sortedTools
            .filter { it.score >= config.similarityThreshold }
            .map { it.tool }
        
        if (filteredTools.isEmpty()) {
            return applyMaxToolsLimit(tools)
        }
        
        return applyMaxToolsLimit(filteredTools)
    }

    private suspend fun filterBySemantic(query: String, tools: List<CactusTool>): List<CactusTool> {
        return try {
            val queryEmbedding = lm.generateEmbedding(text = query)
            if (queryEmbedding?.success != true) {
                return filterByEnhancedKeyword(query, tools)
            }

            val scoredTools = mutableListOf<ScoredTool>()
            for (tool in tools) {
                val toolText = "${tool.function.name}: ${tool.function.description}\nParameters: ${tool.function.parameters.properties.keys.joinToString(", ")}"
                val toolEmbedding = lm.generateEmbedding(text = toolText)
                
                if (toolEmbedding?.success == true) {
                    val similarity = cosineSimilarity(queryEmbedding.embeddings, toolEmbedding.embeddings)
                    scoredTools.add(ScoredTool(tool, similarity))
                }
            }

            val sortedTools = scoredTools.sortedByDescending { it.score }
            val filteredTools = sortedTools
                .filter { it.score >= config.similarityThreshold }
                .map { it.tool }

            if (filteredTools.isEmpty()) {
                return filterByEnhancedKeyword(query, tools)
            }

            applyMaxToolsLimit(filteredTools)
        } catch (e: Exception) {
            filterByEnhancedKeyword(query, tools)
        }
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size) return 0.0
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        if (normA == 0.0 || normB == 0.0) return 0.0
        
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun calculateToolScore(tool: CactusTool, queryWords: List<String>): Double {
        if (queryWords.isEmpty()) return 0.0
        
        val toolText = "${tool.function.name} ${tool.function.description}".lowercase()
        val matchedWords = queryWords.count { word -> toolText.contains(word) }
        
        return matchedWords.toDouble() / queryWords.size
    }

    private fun extractKeywords(query: String): List<String> {
        return query
            .lowercase()
            .replace(Regex("[^\\w\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
    }

    private fun applyMaxToolsLimit(tools: List<CactusTool>): List<CactusTool> {
        val maxTools = config.maxTools
        if (maxTools == null || tools.size <= maxTools) {
            return tools
        }
        return tools.take(maxTools)
    }

    private data class ScoredTool(
        val tool: CactusTool,
        val score: Double
    )
}
