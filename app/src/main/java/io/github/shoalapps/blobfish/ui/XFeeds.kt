package io.github.shoalapps.blobfish.ui

/** Session memory only. Never creates FeedCache or writes X posts or anchors to disk. */
object XFeeds {
    private val feeds = mutableMapOf<Boolean, ReadingFeedVm>()
    private var generation = 0
    fun home(following: Boolean): ReadingFeedVm = feeds.getOrPut(following) {
        val owner = generation
        ReadingFeedVm(null, active = { owner == generation }) { cursor -> XBridge.home(following, cursor) }
    }
    fun clear() { generation++; feeds.clear() }
}
