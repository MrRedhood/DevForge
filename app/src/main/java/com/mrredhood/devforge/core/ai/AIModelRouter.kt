package com.mrredhood.devforge.core.ai

import com.mrredhood.devforge.core.settings.AiRoutingMode

object AIModelRouter {
    fun choose(
        models: List<AIModelInfo>,
        savedModelId: String?,
        mode: AiRoutingMode,
    ): AIModelInfo? {
        if (models.isEmpty()) return null
        if (mode == AiRoutingMode.FIXED) {
            return savedModelId?.let { id -> models.firstOrNull { it.id == id } } ?: models.first()
        }
        return when (mode) {
            AiRoutingMode.LOW_COST -> models.minWithOrNull(compareBy<AIModelInfo>(
                { if (it.priceClass == ModelPriceClass.FREE) 0 else 1 },
                { it.inputPricePerMillion ?: Double.MAX_VALUE },
                { it.outputPricePerMillion ?: Double.MAX_VALUE },
                { it.id },
            ))
            AiRoutingMode.QUALITY -> models.maxWithOrNull(compareBy<AIModelInfo>(
                { if (it.supportsTools) 1 else 0 },
                { it.contextLimit ?: 0L },
                { it.outputTokenLimit ?: 0L },
                { if (it.priceClass == ModelPriceClass.PAID) 1 else 0 },
                { it.id },
            ))
            AiRoutingMode.BALANCED -> models.minWithOrNull(compareBy<AIModelInfo>(
                { if (it.supportsTools) 0 else 1 },
                { if (it.priceClass == ModelPriceClass.FREE) 0 else 1 },
                { it.inputPricePerMillion ?: Double.MAX_VALUE },
                { it.outputPricePerMillion ?: Double.MAX_VALUE },
                { -(it.contextLimit ?: 0L) },
                { it.id },
            ))
            AiRoutingMode.FIXED -> null
        }
    }
}
