package com.ruda.survey.data.repository

import com.ruda.survey.data.dto.LoginRequest
import com.ruda.survey.data.dto.LoginResponse
import com.ruda.survey.data.dto.UserDto
import com.ruda.survey.data.remote.AuthApi
import com.ruda.survey.utils.TokenManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import retrofit2.Response

class AuthRepositoryImplTest {

    private lateinit var repository: AuthRepositoryImpl
    private lateinit var api: AuthApi
    private lateinit var tokenManager: TokenManager

    @Before
    fun setup() {
        api = mock()
        tokenManager = mock()
        repository = AuthRepositoryImpl(api, tokenManager)
    }

    @Test
    fun `login success saves token and returns success`() = runTest {
        val userDto = UserDto("id123", "Shamoon", "shamoon@gmail.com", "admin", 0)
        val loginResponse = LoginResponse(true, "Success", userDto, "test-token")
        whenever(api.login(any())).thenReturn(Response.success(loginResponse))

        val result = repository.login("shamoon@gmail.com", "12121212")

        assertTrue(result.isSuccess)
        val authState = result.getOrNull()!!
        assertTrue(authState.isLoggedIn)
        assertEquals("Shamoon", authState.username)
        verify(tokenManager).saveTokens("test-token", "")
    }

    @Test
    fun `login failure returns error`() = runTest {
        whenever(api.login(any())).thenReturn(Response.error(401, okhttp3.ResponseBody.create(null, "")))

        val result = repository.login("wrong@email.com", "wrong")

        assertTrue(result.isFailure)
        verify(tokenManager, never()).saveTokens(any(), any())
    }
}
