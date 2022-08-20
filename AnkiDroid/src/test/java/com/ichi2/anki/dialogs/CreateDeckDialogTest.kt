/****************************************************************************************
 * Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>                        *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.dialogs

import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.input.getInputField
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.libanki.DeckManager
import com.ichi2.libanki.backend.exception.DeckRenameException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateDeckDialogTest : RobolectricTest() {
    private var mActivityScenario: ActivityScenario<DeckPicker>? = null
    override fun setUp() {
        super.setUp()
        ensureCollectionLoadIsSynchronous()
        mActivityScenario = ActivityScenario.launch(DeckPicker::class.java)
        val activityScenario: ActivityScenario<DeckPicker>? = mActivityScenario
        activityScenario?.moveToState(Lifecycle.State.STARTED)
    }

    @Test
    fun testCreateFilteredDeckFunction() {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.FILTERED_DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col.decks
                    MatcherAssert.assertThat(id, equalTo(decks.id(deckName)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createFilteredDeck(deckName)
            MatcherAssert.assertThat(isCreated.get() as Boolean, equalTo(true))
        }
    }

    @Test
    @Throws(DeckRenameException::class)
    fun testCreateSubDeckFunction() {
        val deckParentId = col.decks.id("Deck Name")
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.SUB_DECK, deckParentId)
            val isCreated = AtomicReference(false)
            val deckName = "filteredDeck"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                try {
                    isCreated.set(true)
                    val decks: DeckManager = activity.col.decks
                    val deckNameWithParentName = decks.getSubdeckName(deckParentId, deckName)
                    MatcherAssert.assertThat(id, equalTo(decks.id(deckNameWithParentName!!)))
                } catch (filteredAncestor: DeckRenameException) {
                    throw RuntimeException(filteredAncestor)
                }
            }
            createDeckDialog.createSubDeck(deckParentId, deckName)
            MatcherAssert.assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun testCreateDeckFunction() {
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            val isCreated = AtomicReference(false)
            val deckName = "Deck Name"
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long ->
                // a deck was created
                isCreated.set(true)
                val decks: DeckManager = activity.col.decks
                MatcherAssert.assertThat(id, equalTo(decks.byName(deckName)!!.getLong("id")))
            }
            createDeckDialog.createDeck(deckName)
            MatcherAssert.assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun testRenameDeckFunction() {
        val deckName = "Deck Name"
        val deckNewName = "New Deck Name"
        mActivityScenario!!.onActivity { activity: DeckPicker ->
            val createDeckDialog = CreateDeckDialog(activity, R.string.new_deck, CreateDeckDialog.DeckDialogType.RENAME_DECK, null)
            createDeckDialog.deckName = deckName
            val isCreated = AtomicReference(false)
            advanceRobolectricLooper()
            createDeckDialog.setOnNewDeckCreated { id: Long? ->
                // a deck name was renamed
                isCreated.set(true)
                val decks: DeckManager = activity.col.decks
                MatcherAssert.assertThat(deckNewName, equalTo(decks.name(id!!)))
            }
            createDeckDialog.renameDeck(deckNewName)
            MatcherAssert.assertThat(isCreated.get(), equalTo(true))
        }
    }

    @Test
    fun nameMayNotBeZeroLength() {
        mActivityScenario!!.onActivity { activity: DeckPicker? ->
            val createDeckDialog = CreateDeckDialog(activity!!, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
            val materialDialog = createDeckDialog.showDialog()
            val actionButton = materialDialog.getActionButton(WhichButton.POSITIVE)
            MatcherAssert.assertThat("Ok is disabled if zero length input", actionButton.isEnabled, equalTo(false))
            val editText: EditText? = Objects.requireNonNull(materialDialog.getInputField())
            editText?.setText("NotEmpty")
            MatcherAssert.assertThat("Ok is enabled if not zero length input", actionButton.isEnabled, equalTo(true))
        }
    }

    @Test
    @Ignore("this is difficult to test at the moment")
    fun searchDecksIconVisibilityDeckCreationTest() {
        // this is currently broken, as it has a few issues:
        // - we need to await the completion of createMenuJob, as the menu is created asynchronously
        // - the calls to `decks` should be made using withCol, and this routine should be asynchronous
        // - when I attempted to implement this, I found the test hung. I'm guessing it might be some
        // sort of deadlock, where a runBlocking() call is waiting for some UI state to update
        mActivityScenario!!.onActivity { deckPicker ->
            val decks = deckPicker.col.decks
            val deckCounter = AtomicInteger(1)
            for (i in 0 until 10) {
                val createDeckDialog = CreateDeckDialog(deckPicker, R.string.new_deck, CreateDeckDialog.DeckDialogType.DECK, null)
                createDeckDialog.setOnNewDeckCreated { did ->
                    assertEquals(deckCounter.incrementAndGet(), decks.count())

                    assertEquals(deckCounter.get(), decks.count())

                    updateSearchDecksIcon(deckPicker)
                    assertEquals(deckPicker.searchDecksIcon!!.isVisible, decks.count() >= 10)

                    // After the last deck was created, delete a deck
                    if (decks.count() >= 10) {
                        awaitJob(deckPicker.confirmDeckDeletion(did))
                        assertEquals(deckCounter.decrementAndGet(), decks.count())

                        assertEquals(deckCounter.get(), decks.count())

                        updateSearchDecksIcon(deckPicker)
                        assertFalse(deckPicker.searchDecksIcon!!.isVisible)
                    }
                }
                createDeckDialog.createDeck("Deck$i")
            }
        }
    }

    private fun updateSearchDecksIcon(deckPicker: DeckPicker) {
        deckPicker.updateDeckList()
        // the icon normally is updated in the background usually; force it to update
        // immediately so that the test can continue
        runBlocking {
            deckPicker.createMenuJob?.join()
        }
    }

    @Test
    fun searchDecksIconVisibilitySubdeckCreationTest() = runTest {
        val deckPicker =
            suspendCoroutine { coro -> mActivityScenario!!.onActivity { coro.resume(it) } }
        deckPicker.updateMenuState()
        assertEquals(deckPicker.optionsMenuState!!.searchIcon, false)
        // a single top-level deck with lots of subdecks should turn the icon on
        withCol {
            decks.id(deckTreeName(0, 10, "Deck"))
        }
        deckPicker.updateMenuState()
        assertEquals(deckPicker.optionsMenuState!!.searchIcon, true)
    }

    private fun deckTreeName(start: Int, end: Int, prefix: String): String {
        return List(end - start + 1) { "${prefix}${it + start}" }
            .joinToString("::")
    }
}
