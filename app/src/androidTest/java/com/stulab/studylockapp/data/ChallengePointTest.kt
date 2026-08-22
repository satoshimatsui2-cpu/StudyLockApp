package com.stulab.studylockapp.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengePointTest {

    private lateinit var context: Context
    private lateinit var cpManager: ChallengePointManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("points", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        cpManager = ChallengePointManager(context)
    }

    @Test
    fun testInitialCP() {
        assertEquals(0, cpManager.getCP())
    }

    @Test
    fun testAddCP() {
        cpManager.addCP(1)
        assertEquals(1, cpManager.getCP())
        cpManager.addCP(5)
        assertEquals(6, cpManager.getCP())
    }

    @Test
    fun testConsumeCP() {
        cpManager.addCP(15)
        val result = cpManager.consumeCP(10)
        assertEquals(true, result)
        assertEquals(5, cpManager.getCP())
    }

    @Test
    fun testConsumeCP_Insufficient() {
        cpManager.addCP(5)
        val result = cpManager.consumeCP(10)
        assertEquals(false, result)
        assertEquals(5, cpManager.getCP())
    }
}
