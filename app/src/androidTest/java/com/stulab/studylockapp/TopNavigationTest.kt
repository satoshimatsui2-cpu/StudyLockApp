package com.stulab.studylockapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopNavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testGradeSelectionDialog() {
        // Click level selection button
        onView(withId(R.id.button_select_grade)).perform(click())

        // Verify Dialog title is displayed
        onView(withText(R.string.label_grade_selection_title)).check(matches(isDisplayed()))

        // Click cancel
        onView(withText("キャンセル")).perform(click())

        // Verify dialog is closed (button should be visible again)
        onView(withId(R.id.button_select_grade)).check(matches(isDisplayed()))
    }

    @Test
    fun testProgressAccordion() {
        // Verify accordion is hidden initially
        onView(withId(R.id.layout_progress_accordion)).check(matches(not(isDisplayed())))

        // Click toggle button
        onView(withId(R.id.button_toggle_progress)).perform(click())

        // Verify accordion is visible
        onView(withId(R.id.layout_progress_accordion)).check(matches(isDisplayed()))

        // Click toggle button again
        onView(withId(R.id.button_toggle_progress)).perform(click())

        // Verify accordion is hidden
        onView(withId(R.id.layout_progress_accordion)).check(matches(not(isDisplayed())))
    }
}
