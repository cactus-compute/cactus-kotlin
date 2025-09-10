package com.cactus.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean = false
)

@Serializable
data class ToolParametersSchema(
    val type: String = "object",
    val properties: Map<String, ToolParameter>,
    val required: List<String>
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: ToolParametersSchema
)

@Serializable
data class Tool(
    val type: String = "function",
    val function: ToolFunction
)

fun List<Tool>.toToolsJson(): String {
    return Json.encodeToString(ListSerializer(Tool.serializer()), this)
}

fun createTool(
    name: String,
    description: String,
    parameters: Map<String, ToolParameter>
): Tool {
    val required = parameters.entries
        .filter { (_, param) -> param.required }
        .map { (key, _) -> key }

    return Tool(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = ToolParametersSchema(
                properties = parameters,
                required = required
            )
        )
    )
}