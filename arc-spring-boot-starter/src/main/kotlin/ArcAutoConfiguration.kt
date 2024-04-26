// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package io.github.lmos.arc.spring

import io.github.lmos.arc.agents.Agent
import io.github.lmos.arc.agents.AgentLoader
import io.github.lmos.arc.agents.AgentProvider
import io.github.lmos.arc.agents.CompositeAgentProvider
import io.github.lmos.arc.agents.dsl.AgentFactory
import io.github.lmos.arc.agents.dsl.BeanProvider
import io.github.lmos.arc.agents.dsl.ChatAgentFactory
import io.github.lmos.arc.agents.dsl.CoroutineBeanProvider
import io.github.lmos.arc.agents.events.BasicEventPublisher
import io.github.lmos.arc.agents.events.EventHandler
import io.github.lmos.arc.agents.events.LoggingEventHandler
import io.github.lmos.arc.agents.events.addAll
import io.github.lmos.arc.agents.functions.CompositeLLMFunctionProvider
import io.github.lmos.arc.agents.functions.LLMFunction
import io.github.lmos.arc.agents.functions.LLMFunctionLoader
import io.github.lmos.arc.agents.functions.LLMFunctionProvider
import io.github.lmos.arc.agents.memory.InMemoryMemory
import io.github.lmos.arc.agents.memory.Memory
import io.github.lmos.arc.scripting.agents.AgentScriptEngine
import io.github.lmos.arc.scripting.agents.CompiledAgentLoader
import io.github.lmos.arc.scripting.agents.KtsAgentScriptEngine
import io.github.lmos.arc.scripting.agents.ScriptingAgentLoader
import io.github.lmos.arc.scripting.functions.FunctionScriptEngine
import io.github.lmos.arc.scripting.functions.KtsFunctionScriptEngine
import io.github.lmos.arc.scripting.functions.ScriptingLLMFunctionLoader
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import java.io.File
import java.time.Duration
import kotlin.reflect.KClass

@AutoConfiguration
class ArcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BeanProvider::class)
    fun beanProvider(beanFactory: ConfigurableBeanFactory): BeanProvider = CoroutineBeanProvider(object : BeanProvider {
        override suspend fun <T : Any> provide(bean: KClass<T>) = beanFactory.getBean(bean.java)
    })

    @Bean
    fun eventPublisher(eventHandlers: List<EventHandler<*>>) = BasicEventPublisher().apply {
        addAll(eventHandlers)
    }

    @Bean
    @ConditionalOnMissingBean(AgentFactory::class)
    fun agentFactory(beanProvider: BeanProvider) = ChatAgentFactory(beanProvider)

    @Bean
    fun functionScriptEngine(): FunctionScriptEngine = KtsFunctionScriptEngine()

    @Bean
    fun agentScriptEngine(): AgentScriptEngine = KtsAgentScriptEngine()

    @Bean
    @ConditionalOnMissingBean(Memory::class)
    fun memory() = InMemoryMemory()

    @Bean
    @ConditionalOnMissingBean(EventHandler::class)
    fun loggingEventHandler() = LoggingEventHandler()

    @Bean
    fun agentProvider(loaders: List<AgentLoader>, agents: List<Agent<*, *>>): AgentProvider =
        CompositeAgentProvider(loaders, agents)

    @Bean(destroyMethod = "destroy")
    fun scriptingAgentLoader(
        agentScriptEngine: AgentScriptEngine,
        agentFactory: AgentFactory<*>,
        compiledAgents: List<CompiledAgentLoader>?,
        @Value("\${arc.scripts.hotReload.enable:false}") hotReload: Boolean,
        @Value("\${arc.scripts.folder:/agents}") agentsFolder: File,
        @Value("\${arc.scripts.hotReload.delay:PT3M}") hotReloadDelay: Duration,
    ): ScriptingAgentLoader {
        return ScriptingAgentLoader(agentFactory, agentScriptEngine).also {
            compiledAgents?.forEach(it::loadCompiledAgent)
            if (hotReload) {
                if (!agentsFolder.exists()) error("Agents folder does not exist: $agentsFolder!")
                it.startHotReload(agentsFolder, hotReloadDelay)
            }
        }
    }

    @Bean
    fun llmFunctionProvider(loaders: List<LLMFunctionLoader>, functions: List<LLMFunction>): LLMFunctionProvider =
        CompositeLLMFunctionProvider(loaders, functions)

    @Bean(destroyMethod = "destroy")
    fun scriptingLLMFunctionProvider(
        functionScriptEngine: FunctionScriptEngine,
        @Value("\${arc.scripts.hotReload.enable:false}") hotReload: Boolean,
        @Value("\${arc.scripts.folder:/agents}") functionsFolder: File,
        @Value("\${arc.scripts.hotReload.delay:PT3M}") hotReloadDelay: Duration,
        beanProvider: BeanProvider,
    ): ScriptingLLMFunctionLoader {
        return ScriptingLLMFunctionLoader(beanProvider, functionScriptEngine).also {
            if (hotReload) {
                if (!functionsFolder.exists()) error("Functions folder does not exist: $functionsFolder!")
                it.startHotReload(functionsFolder, hotReloadDelay)
            }
        }
    }

    @Bean
    fun agentLoader(agentFactory: AgentFactory<*>) = Agents(agentFactory)

    @Bean
    fun functionLoader(beanProvider: BeanProvider) = Functions(beanProvider)
}
