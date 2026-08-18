package com.stulab.studylockapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stulab.studylockapp.data.AdminAuthManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setup() {
        // Ensure admin lock is disabled for testing ease
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        AdminAuthManager.setAdminLockEnabled(context, false)
    }

    @Test
    fun testMyWordBookNavigationMoved() {
        // 1. Verify "My Word Book" is NOT on MainActivity by text
        // (Since the ID was removed, we use text to verify it's gone)
        onView(withText("マイ単語帳")).check(doesNotExist())

        // 2. Open Admin Settings
        onView(withId(R.id.button_admin_settings_top)).perform(click())

        // 3. Expand "Word Data Management" accordion if needed
        // The id is header_personal_wordbook
        onView(withId(R.id.header_personal_wordbook)).perform(click())

        // 4. Verify "My Word Book" (button_manage_personal_words) exists
        onView(withId(R.id.button_manage_personal_words)).check(matches(isDisplayed()))
        onView(withText("マイ単語帳")).check(matches(isDisplayed()))

        // 5. Click it and verify MyWordBookActivity opens
        onView(withId(R.id.button_manage_personal_words)).perform(click())
        
        // Check for something unique to MyWordBookActivity, e.g., recycler_slots
        onView(withId(R.id.recycler_slots)).check(matches(isDisplayed()))

        // 6. Go back and verify we are in AdminSettingsActivity
        pressBack()
        onView(withId(R.id.header_personal_wordbook)).check(matches(isDisplayed()))

        // 7. Go back again and verify we are in MainActivity
        pressBack()
        onView(withId(R.id.button_admin_settings_top)).check(matches(isDisplayed()))
    }
}
