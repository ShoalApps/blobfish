package io.github.shoalapps.blobfish.data

import org.junit.Assert.*
import org.junit.Test

class AuthorFeedRequestTest {
    @Test fun pinsAreRequestedOnlyForPostsIncludingPagination() {
        for (cursor in listOf(null, "next")) {
            for (filter in listOf("posts_and_author_threads", "posts_with_replies", "posts_with_media")) {
                val params = authorFeedParameters("did:plc:author", cursor, filter)
                assertEquals("did:plc:author", params["actor"])
                assertEquals(cursor, params["cursor"])
                assertEquals(filter, params["filter"])
                assertEquals("30", params["limit"])
                assertEquals((filter == "posts_and_author_threads").toString(), params["includePins"])
            }
        }
    }
}
