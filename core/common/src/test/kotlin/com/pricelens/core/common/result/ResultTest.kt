package com.pricelens.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTest {

    @Test
    fun `map should transform success value`() {
        val result = Result.Success(10).map { it * 2 }
        assertEquals(Result.Success(20), result)
    }

    @Test
    fun `map should pass through failure`() {
        val error = AppError.UnknownError("error")
        val result = Result.Failure(error).map { 10 }
        assertEquals(Result.Failure(error), result)
    }

    @Test
    fun `flatMap should transform success to new result`() {
        val result = Result.Success(10).flatMap { Result.Success(it * 2) }
        assertEquals(Result.Success(20), result)
    }

    @Test
    fun `onSuccess should be called for success`() {
        var called = false
        Result.Success(10).onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `onFailure should be called for failure`() {
        var called = false
        Result.Failure(AppError.UnknownError("error")).onFailure { called = true }
        assertTrue(called)
    }
}
