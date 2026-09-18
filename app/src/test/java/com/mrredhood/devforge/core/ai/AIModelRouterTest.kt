package com.mrredhood.devforge.core.ai

import com.mrredhood.devforge.core.settings.AiRoutingMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AIModelRouterTest {
    private val models = listOf(
        AIModelInfo(AIProvider.OPENAI, "expensive", "Expensive", priceClass = ModelPriceClass.PAID, inputPricePerMillion = 5.0, outputPricePerMillion = 15.0, contextLimit = 200_000, outputTokenLimit = 20_000, supportsTools = true),
        AIModelInfo(AIProvider.OPENAI, "free", "Free", priceClass = ModelPriceClass.FREE, inputPricePerMillion = 0.0, outputPricePerMillion = 0.0, contextLimit = 32_000),
        AIModelInfo(AIProvider.OPENAI, "balanced", "Balanced", priceClass = ModelPriceClass.PAID, inputPricePerMillion = 1.0, outputPricePerMillion = 4.0, contextLimit = 128_000, outputTokenLimit = 8_000, supportsTools = true),
    )

    @Test
    fun fixedUsesSavedModel() {
        assertEquals("balanced", AIModelRouter.choose(models, "balanced", AiRoutingMode.FIXED)?.id)
    }

    @Test
    fun lowCostPrefersFree() {
        assertEquals("free", AIModelRouter.choose(models, null, AiRoutingMode.LOW_COST)?.id)
    }

    @Test
    fun embeddingModelsAreExcludedFromChatRouting() {
        val embedding = AIModelInfo(AIProvider.OPENAI, "embedding", "Embedding", outputModalities = setOf("embedding"))
        assertEquals("free", AIModelRouter.choose(models + embedding, null, AiRoutingMode.LOW_COST)?.id)
    }

    @Test
    fun qualityPrefersToolCapableLargeContext() {
        assertEquals("expensive", AIModelRouter.choose(models, null, AiRoutingMode.QUALITY)?.id)
    }
}
