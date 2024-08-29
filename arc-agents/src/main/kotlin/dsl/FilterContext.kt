// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package ai.ancf.lmos.arc.agents.dsl

import ai.ancf.lmos.arc.agents.conversation.Conversation
import ai.ancf.lmos.arc.agents.conversation.ConversationMessage
import kotlin.reflect.KClass

/**
 * Context for filtering messages before being processed by an Agent.
 */
class InputFilterContext(
    scriptingContext: DSLContext,
    @Volatile var input: Conversation,
) : FilterContext(scriptingContext) {

    /**
     * The input message.
     */
    var inputMessage
        get() = input.transcript.last()
        set(message) {
            val updatedConversation = input.removeLast()
            input = updatedConversation + message
        }

    override suspend fun map(filter: suspend (ConversationMessage) -> ConversationMessage?) {
        input = input.map { msg -> filter(msg) }
    }

    override suspend fun mapLatest(filter: suspend (ConversationMessage) -> ConversationMessage?) {
        input = input.mapLatest { msg -> filter(msg) }
    }
}

/**
 * Context for filtering messages after being processed by an Agent.
 */
class OutputFilterContext(
    scriptingContext: DSLContext,
    val input: Conversation,
    @Volatile var output: Conversation,
    val systemPrompt: String,
) : FilterContext(scriptingContext) {

    /**
     * The message generated by the Agent.
     */
    var outputMessage
        get() = output.transcript.last()
        set(message) {
            output = input + message
        }

    override suspend fun map(filter: suspend (ConversationMessage) -> ConversationMessage?) {
        output = output.map { msg -> filter(msg) }
    }

    override suspend fun mapLatest(filter: suspend (ConversationMessage) -> ConversationMessage?) {
        output = output.mapLatest { msg -> filter(msg) }
    }
}

abstract class FilterContext(scriptingContext: DSLContext) : DSLContext by scriptingContext {
    suspend infix fun String.replaces(s: String) = this@FilterContext.mapLatest {
        it.update(it.content.replace(s, this))
    }

    suspend infix fun String.replaces(s: Regex) = this@FilterContext.mapLatest {
        it.update(it.content.replace(s, this))
    }

    suspend operator fun String.unaryMinus() {
        this@FilterContext.mapLatest {
            it.update(it.content.replace(this, ""))
        }
    }

    suspend operator fun Regex.unaryMinus() {
        this@FilterContext.mapLatest {
            it.update(it.content.replace(this, ""))
        }
    }

    suspend operator fun AgentFilter.unaryPlus() {
        this@FilterContext.mapLatest { msg -> filter(msg) }
    }

    suspend operator fun KClass<out AgentFilter>.unaryPlus() {
        this@FilterContext.mapLatest { msg -> context(this).filter(msg) }
    }

    /**
     * Maps all message in a Conversation transcript.
     */
    abstract suspend fun map(filter: suspend (ConversationMessage) -> ConversationMessage?)

    /**
     * Maps the latest message in a Conversation transcript.
     */
    abstract suspend fun mapLatest(filter: suspend (ConversationMessage) -> ConversationMessage?)
}

/**
 * Filters are used to modify or remove messages from the conversation transcript.
 */
fun interface AgentFilter {

    /**
     * Filters or transform Conversation Messages.
     * If the fun returns null, the message will be removed from the conversation transcript.
     */
    suspend fun filter(message: ConversationMessage): ConversationMessage?
}
