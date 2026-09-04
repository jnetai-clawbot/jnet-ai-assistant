package com.jnetai.assistant

import com.jnetai.assistant.agent.EvalShuntingYard
import com.jnetai.assistant.agent.ToolArgs
import com.jnetai.assistant.agent.AgentTool
import com.jnetai.assistant.agent.ToolParam
import com.jnetai.assistant.agent.PermissionKind
import com.jnetai.assistant.agent.SafetyLevel
import com.jnetai.assistant.agent.AgentException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MathEvaluatorTest {

    @Test
    fun `simple arithmetic`() {
        assertEquals(5.0, EvalShuntingYard.eval("2 + 3"), 1e-6)
        assertEquals(4.0, EvalShuntingYard.eval("2 * 2"), 1e-6)
        assertEquals(1.0, EvalShuntingYard.eval("3 - 2"), 1e-6)
        assertEquals(3.0, EvalShuntingYard.eval("6 / 2"), 1e-6)
    }

    @Test
    fun `parentheses and precedence`() {
        assertEquals(6.0, EvalShuntingYard.eval("2 * (1 + 2)"), 1e-6)
        assertEquals(7.0, EvalShuntingYard.eval("1 + 2 * 3"), 1e-6)
        assertEquals(8.0, EvalShuntingYard.eval("2 ^ 3"), 1e-6)
    }

    @Test
    fun `negative numbers`() {
        assertEquals(-5.0, EvalShuntingYard.eval("-5"), 1e-6)
        assertEquals(-2.0, EvalShuntingYard.eval("3 + -5"), 1e-6)
    }

    @Test
    fun `rejects unknowns and bad syntax`() {
        val bad = listOf("2 + 3 * % 5", "sin(3)", "cat file", "$(rm)")
        bad.forEach { expr ->
            runCatching { EvalShuntingYard.eval(expr) }.let { r ->
                assertTrue("expected failure for '$expr'", r.isFailure)
            }
        }
    }

    @Test
    fun `division by zero fails`() {
        runCatching { EvalShuntingYard.eval("5 / 0") }.let { assertTrue(it.isFailure) }
    }
}

class ToolArgsTest {

    private val tool = AgentTool(
        name = "open_url",
        description = "open a url",
        parameters = listOf(ToolParam("url", "string", "http(s) url")),
        permission = PermissionKind.NETWORK,
        safety = SafetyLevel.PRIVACY_SENSITIVE
    )

    @Test
    fun `valid args pass`() {
        val args = ToolArgs.validate(tool, """{"url":"https://example.com"}""")
        assertEquals("https://example.com", args["url"])
    }

    @Test
    fun `missing required arg rejects`() {
        try {
            ToolArgs.validate(tool, "{}")
            fail("expected validation failure")
        } catch (e: AgentException.AgentArgs) {
            assertTrue(e.message!!.contains("url"))
        }
    }

    @Test
    fun `malformed json rejects`() {
        runCatching { ToolArgs.validate(tool, "not json") }
            .let { assertTrue(it.isFailure) }
    }

    @Test
    fun `wrong type rejects`() {
        runCatching { ToolArgs.validate(tool, """{"url": 42}""") }
            .let { assertTrue(it.isFailure) }
    }
}