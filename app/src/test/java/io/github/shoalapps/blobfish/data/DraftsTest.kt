package io.github.shoalapps.blobfish.data

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class DraftsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directory get() = File(temporary.root, "drafts")
    private fun store() = Drafts(directory)
    private fun file(picture: DraftPicture) = File(java.net.URI(picture.uri))

    @Test fun restoresTextReplyPicturesAndAltTextAfterRestart() {
        val drafts = store()
        val picture = drafts.copyPicture("account", "at://reply") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
            .copy(alt = "A little fish")
        val draft = Draft("Hello 🐟\nworld", "at://reply", listOf(picture))
        drafts.save("account", draft)
        assertEquals(draft, store().load("account", "at://reply"))
        assertArrayEquals(byteArrayOf(1, 2, 3), file(picture).readBytes())
    }

    @Test fun accountsAndReplyTargetsHaveSeparateDrafts() {
        val drafts = store()
        val entries = listOf("a" to Draft("New"), "a" to Draft("Reply", "target"),
            "a" to Draft("Another reply", "other"), "b" to Draft("Other account"),
            "a|b" to Draft("Delimiters", "c"), "a" to Draft("Different key", "b|c"))
        entries.forEach { (owner, draft) -> drafts.save(owner, draft) }
        entries.forEach { (owner, draft) -> assertEquals(draft, store().load(owner, draft.replyTo)) }
        assertNull(drafts.load("missing", null))
        assertNull(drafts.load("b", "target"))
    }

    @Test fun updatesReplaceTheDraftAndDeleteOnlyRemovedPictures() {
        val drafts = store()
        val first = drafts.copyPicture("a", null) { ByteArrayInputStream(byteArrayOf(1)) }
        val second = drafts.copyPicture("a", null) { ByteArrayInputStream(byteArrayOf(2)) }
        val other = drafts.copyPicture("b", null) { ByteArrayInputStream(byteArrayOf(3)) }
        drafts.save("a", Draft("First", pictures = listOf(first, second)))
        drafts.save("b", Draft(pictures = listOf(other)))
        val updated = Draft("Edited", pictures = listOf(second.copy(alt = "Updated description")))
        drafts.save("a", updated)
        assertEquals(updated, store().load("a", null))
        assertFalse(file(first).exists())
        assertTrue(file(second).exists())
        assertTrue(file(other).exists())
    }

    @Test fun discardDeletesCopiesIncludingUnfinishedImportsButKeepsOtherDrafts() {
        val drafts = store()
        val picture = drafts.copyPicture("a", null) { ByteArrayInputStream(byteArrayOf(1)) }
        val unfinished = drafts.copyPicture("a", null) { ByteArrayInputStream(byteArrayOf(2)) }
        drafts.save("a", Draft(pictures = listOf(picture)))
        drafts.save("a", Draft("Reply", "target"))
        drafts.save("b", Draft("Keep me"))
        drafts.discard("a", null)
        assertNull(store().load("a", null))
        assertFalse(file(picture).exists())
        assertFalse(file(unfinished).exists())
        assertEquals("Reply", store().load("a", "target")?.text)
        assertEquals("Keep me", store().load("b", null)?.text)
        drafts.discard("a", null)
    }

    @Test fun failedCopyLeavesNoPartialPicture() {
        val drafts = store()
        val existing = Draft("Keep me")
        drafts.save("a", existing)
        val before = directory.walkTopDown().filter { it.isFile }.toList().toSet()
        assertThrows(IOException::class.java) {
            drafts.copyPicture("a", null) { object : InputStream() {
                override fun read(): Int = throw IOException("Interrupted picker")
            } }
        }
        assertEquals(before, directory.walkTopDown().filter { it.isFile }.toList().toSet())
        assertEquals(existing, store().load("a", null))
    }

    @Test fun failedAtomicReplacementKeepsThePreviousDraftAndPictures() {
        val drafts = store()
        val picture = drafts.copyPicture("a", null) { ByteArrayInputStream(byteArrayOf(1)) }
        val existing = Draft("Keep me", pictures = listOf(picture))
        drafts.save("a", existing)
        val folder = file(picture).parentFile!!
        // A directory at the staging path simulates a storage failure before replacement.
        File(folder, "draft.tmp").mkdir()
        assertThrows(IOException::class.java) { drafts.save("a", Draft("Replacement")) }
        assertEquals(existing, store().load("a", null))
        assertTrue(file(picture).exists())
    }

    @Test fun interruptedStagingWriteDoesNotReplaceCommittedDraft() {
        val drafts = store()
        drafts.save("a", Draft("Saved"))
        val committed = directory.walkTopDown().first { it.name == "draft.json" }
        File(committed.parentFile, "draft.tmp").writeText("{partial")
        assertEquals(Draft("Saved"), store().load("a", null))
        drafts.save("a", Draft("Next"))
        assertEquals(Draft("Next"), store().load("a", null))
    }

    @Test fun cleanupNeverDeletesFilesOutsideTheDraftFolder() {
        val drafts = store()
        val external = temporary.newFile("original.jpg").apply { writeText("Original") }
        drafts.save("a", Draft(pictures = listOf(DraftPicture(external.toURI().toString()))))
        drafts.save("a", Draft("Edited"))
        drafts.discard("a", null)
        assertEquals("Original", external.readText())
    }

    @Test fun corruptDraftIsReportedAndNotOverwritten() {
        val drafts = store()
        drafts.save("a", Draft("Saved"))
        val committed = directory.walkTopDown().first { it.name == "draft.json" }
        committed.writeText("{broken")
        assertThrows(Exception::class.java) { drafts.load("a", null) }
        assertThrows(Exception::class.java) { drafts.save("a", Draft("Replacement")) }
        assertEquals("{broken", committed.readText())
        drafts.discard("a", null)
        assertNull(drafts.load("a", null))
    }

    @Test fun listingIsNewestFirstAndUsesSavedTimeAfterRestart() {
        var time = 1000L
        val drafts = Drafts(directory) { time }
        val picture = drafts.copyPicture("a", "reply") { ByteArrayInputStream(byteArrayOf(1)) }
        val reply = Draft("Reply", "reply", listOf(picture), DraftReply("parent-cid", "root-uri", "root-cid"))
        drafts.save("a", reply)
        time = 2000L
        drafts.save("a", Draft("New post"))
        assertEquals(listOf(SavedDraft(Draft("New post"), 2000L), SavedDraft(reply, 1000L)), store().list("a"))
        time = 3000L
        drafts.save("a", reply.copy(text = "Edited reply"))
        assertEquals(listOf("Edited reply", "New post"), store().list("a").map { it.draft.text })
        assertEquals(3000L, store().list("a").first().savedAt)
        assertEquals(reply.replyTarget, store().load("a", "reply")?.replyTarget)
    }

    @Test fun listingSeparatesAccountsIncludingTheSameDidOnDifferentServers() {
        val drafts = store()
        val owners = listOf("did:a|https://one", "did:a|https://two", "did:b|https://one", "a|b", "a")
        owners.forEach { owner ->
            drafts.save(owner, Draft("Post for $owner"))
            drafts.save(owner, Draft("Reply for $owner", "same-target"))
        }
        owners.forEach { owner ->
            assertEquals(setOf("Post for $owner", "Reply for $owner"), store().list(owner).map { it.draft.text }.toSet())
        }
        assertTrue(drafts.list("signed-out").isEmpty())
        drafts.discard(owners.first(), "same-target")
        assertEquals(1, drafts.list(owners.first()).size)
        assertEquals(2, drafts.list(owners[1]).size)
    }

    @Test fun legacyDraftsUseTheFileTimeWithoutBeingRewritten() {
        val drafts = Drafts(directory) { 2000L }
        drafts.save("a", Draft("Legacy", "target"))
        val file = directory.walkTopDown().first { it.name == "draft.json" }
        val legacy = """{"text":"Legacy","replyTo":"target","pictures":[]}"""
        file.writeText(legacy)
        assertTrue(file.setLastModified(1000L))
        drafts.save("a", Draft("Recent"))
        assertEquals(listOf(2000L, 1000L), store().list("a").map { it.savedAt })
        assertEquals(Draft("Legacy", "target"), store().list("a").last().draft)
        assertTrue(store().list("b").isEmpty())
        assertEquals(legacy, file.readText())
    }

    @Test fun listingSkipsBrokenFilesAndUnfinishedFoldersWithoutLosingGoodDrafts() {
        val drafts = store()
        drafts.save("a", Draft("Good"))
        drafts.save("a", Draft("Broken", "broken"))
        val broken = directory.walkTopDown().first { it.name == "draft.json" && it.readText().contains("Broken") }
        broken.writeText("{broken")
        drafts.save("a", Draft("Wrong type", "wrong-type"))
        val wrongType = directory.walkTopDown().first { it.name == "draft.json" && it.readText().contains("Wrong type") }
        wrongType.writeText("""{"text":[],"replyTo":"wrong-type"}""")
        drafts.save("a", Draft("Bad time", "bad-time"))
        val badTime = directory.walkTopDown().first { it.name == "draft.json" && it.readText().contains("Bad time") }
        badTime.writeText("""{"text":"Bad time","replyTo":"bad-time","savedAt":"yesterday"}""")
        drafts.save("a", Draft("Bad reply", "bad-reply"))
        val badReply = directory.walkTopDown().first { it.name == "draft.json" && it.readText().contains("Bad reply") }
        badReply.writeText("""{"text":"Bad reply","replyTo":"bad-reply","replyTarget":{"cid":"parent","rootUri":"root"}}""")
        drafts.copyPicture("a", "unfinished") { ByteArrayInputStream(byteArrayOf(1)) }
        File(directory, "stray-file").writeText("Not a folder")
        File(directory, "misplaced").mkdir()
        File(directory, "misplaced/draft.json").writeText("""{"text":"Wrong folder"}""")
        assertEquals(listOf("Good"), store().list("a").map { it.draft.text })
        assertEquals("{broken", broken.readText())
        assertTrue(wrongType.exists())
        assertEquals("Good", store().load("a", null)?.text)
    }

    @Test fun listingAnAbsentDirectoryIsEmptyAndDiscardUpdatesTheList() {
        val drafts = store()
        assertTrue(drafts.list("a").isEmpty())
        assertFalse(directory.exists())
        drafts.save("a", Draft("Saved"))
        val savedRevision = drafts.changes.value
        assertEquals(1, drafts.list("a").size)
        drafts.discard("a", null)
        assertTrue(drafts.changes.value > savedRevision)
        assertTrue(store().list("a").isEmpty())
    }

    @Test fun anEmptyDraftLeavesNothingBehind() {
        val drafts = store()
        drafts.save("account", Draft("words"))
        drafts.save("account", Draft("  "))
        assertNull(drafts.load("account", null))
        assertEquals(0, directory.listFiles().orEmpty().size)
    }
}
