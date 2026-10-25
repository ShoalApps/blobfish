@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.swordfishapps.social.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.withStyle
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import io.github.swordfishapps.social.data.Moderation
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.swordfishapps.social.Graph
import io.github.swordfishapps.social.data.Author
import io.github.swordfishapps.social.data.Post
import io.github.swordfishapps.social.data.RichText
import io.github.swordfishapps.social.data.compact
import io.github.swordfishapps.social.data.shortAgo
import io.github.swordfishapps.social.data.webUrl
import kotlinx.coroutines.launch

/** Where links inside posts lead. */
class Nav(
    val post: (String) -> Unit,
    val profile: (String) -> Unit,
    val tag: (String) -> Unit,
    val reply: (Post) -> Unit,
)

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (url == null) Icon(Icons.Rounded.AccountCircle, null, Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** A post's text with mu-style formatting: paragraphs with facets and markdown, and highlighted code blocks. */
@Composable
fun RichTextView(text: String, facets: List<RichText.Facet>, nav: Nav, maxLines: Int = Int.MAX_VALUE, large: Boolean = false) {
    val markdown = Graph.store.settings.collectAsState().value.richFormatting
    val blocks = remember(text, facets, markdown) { RichText.parse(text, facets, markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is RichText.Paragraph -> {
                    val annotated = remember(b, linkColor, codeBg) {
                        buildAnnotatedString {
                            append(b.text)
                            for (s in b.spans) when (s) {
                                is RichText.Styled -> addStyle(
                                    when (s.style) {
                                        RichText.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                                        RichText.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                                        RichText.Style.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                                        RichText.Style.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 14.sp)
                                    },
                                    s.start, s.end,
                                )
                                is RichText.Link -> addLink(LinkAnnotation.Url(s.uri, TextLinkStyles(SpanStyle(color = linkColor))), s.start, s.end)
                                is RichText.Mention -> addLink(LinkAnnotation.Clickable("m", TextLinkStyles(SpanStyle(color = linkColor))) { nav.profile(s.did) }, s.start, s.end)
                                is RichText.Tag -> addLink(LinkAnnotation.Clickable("t", TextLinkStyles(SpanStyle(color = linkColor))) { nav.tag(s.tag) }, s.start, s.end)
                            }
                        }
                    }
                    Text(annotated, style = if (large) MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp) else MaterialTheme.typography.bodyLarge, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
                }
                is RichText.Code -> CodeBlock(b, context)
            }
        }
    }
}

@Composable
private fun CodeBlock(b: RichText.Code, context: Context) {
    val keyword = MaterialTheme.colorScheme.primary
    val string = MaterialTheme.colorScheme.tertiary
    val number = MaterialTheme.colorScheme.secondary
    val comment = MaterialTheme.colorScheme.outline
    val highlighted = remember(b, keyword, string, number, comment) {
        buildAnnotatedString {
            append(b.code)
            RichText.highlight(b.code).forEach { h ->
                addStyle(
                    when (h.token) {
                        RichText.Token.KEYWORD -> SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold)
                        RichText.Token.STRING -> SpanStyle(color = string)
                        RichText.Token.NUMBER -> SpanStyle(color = number)
                        RichText.Token.COMMENT -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
                    },
                    h.start, h.end,
                )
            }
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.language ?: "code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", b.code))
                    toast(context, "Code copied")
                }) { Icon(Icons.Rounded.ContentCopy, "Copy code", Modifier.size(18.dp)) }
            }
            Text(
                highlighted,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
fun PostCard(
    post: Post,
    nav: Nav,
    modifier: Modifier = Modifier,
    repostedBy: Author? = null,
    replyTo: Author? = null,
    big: Boolean = false,
    /** In threads a hidden post leaves a note, so the conversation still makes sense. */
    noteWhenHidden: Boolean = false,
    /** Bluesky's thread line, joining this post's avatar to the post above and/or below. */
    lineAbove: Boolean = false,
    lineBelow: Boolean = false,
) {
    val prefs by Graph.store.moderation.collectAsState()
    val signedIn = Graph.store.session.collectAsState().value != null
    val decision = remember(post, prefs, signedIn) { Moderation.decide(post, prefs, signedIn) }
    var revealed by remember(post.uri) { mutableStateOf(false) }
    if (decision is Moderation.Decision.Hide && !revealed) {
        if (noteWhenHidden) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(decision.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).weight(1f))
                // Your own choices can be undone here; moderators' and blocks can't.
                if (decision.reason == "Post hidden by you" || decision.reason.startsWith("Muted word")) TextButton(onClick = { revealed = true }) { Text("Show") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
        return
    }
    val covered = decision is Moderation.Decision.Warn && !revealed
    val blurMedia = decision is Moderation.Decision.BlurMedia && !revealed
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.fillMaxWidth().clickable(enabled = !big && !covered) { nav.post(post.uri) }
            .drawBehind {
                // Centre of the 40dp avatar, 16dp in; the avatar spans 12dp to 52dp from the top.
                val x = 36.dp.toPx()
                val w = 2.dp.toPx()
                if (lineAbove) drawRect(lineColor, Offset(x - w / 2, 0f), Size(w, 10.dp.toPx()))
                if (lineBelow) drawRect(lineColor, Offset(x - w / 2, 54.dp.toPx()), Size(w, size.height - 54.dp.toPx()))
            }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (lineBelow) 4.dp else 12.dp),
    ) {
        repostedBy?.let {
            Row(Modifier.padding(start = 46.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Repeat, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Reposted by ${it.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row {
            Avatar(post.author.avatar, if (big) 48.dp else 40.dp, Modifier.clickable { nav.profile(post.author.did) })
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    VerifiedBadge(post.author, 15.dp)
                    Text(
                        " @${post.author.handle} · ${shortAgo(post.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                replyTo?.let {
                    // Bluesky's reply line: a small arrow and the name, under the author's name.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Reply to ${it.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (covered) {
                    ContentCover((decision as Moderation.Decision.Warn).reason, Modifier.padding(top = 8.dp)) { revealed = true }
                    Actions(post, nav)
                    return@Column
                }
                if (post.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    RichTextView(post.text, post.facets, nav)
                }
                if (blurMedia && (post.images.isNotEmpty() || post.card != null)) {
                    ContentCover((decision as Moderation.Decision.BlurMedia).reason, Modifier.padding(top = 10.dp), media = true) { revealed = true }
                } else {
                    if (post.images.isNotEmpty()) Images(post, Modifier.padding(top = 10.dp))
                    post.card?.let { LinkCardView(it, Modifier.padding(top = 10.dp)) }
                }
                post.quote?.let { q -> QuoteCard(q, nav, Modifier.padding(top = 10.dp)) }
                Actions(post, nav)
            }
        }
    }
    if (!lineBelow) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

/** Bluesky's verification badges: a blue tick for verified accounts, a scalloped one for trusted verifiers. */
@Composable
fun VerifiedBadge(author: Author, size: androidx.compose.ui.unit.Dp) {
    when {
        author.verifier -> Icon(Icons.Rounded.Verified, "Trusted verifier", Modifier.padding(start = 3.dp).size(size), tint = Color(0xFF1083FE))
        author.verified -> Icon(Icons.Rounded.CheckCircle, "Verified", Modifier.padding(start = 3.dp).size(size), tint = Color(0xFF1083FE))
    }
}

/**
 * The post a thread is about, laid out like Bluesky: author at the top, larger text across the full
 * width, the full date, then counts and actions between dividers.
 */
@Composable
fun AnchorPost(post: Post, nav: Nav, lineAbove: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val prefs by Graph.store.moderation.collectAsState()
    val signedIn = Graph.store.session.collectAsState().value != null
    val decision = remember(post, prefs, signedIn) { Moderation.decide(post, prefs, signedIn) }
    var revealed by remember(post.uri) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .drawBehind { if (lineAbove) drawRect(lineColor, Offset(36.dp.toPx() - 1.dp.toPx(), 0f), Size(2.dp.toPx(), 10.dp.toPx())) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.author.avatar, 40.dp, Modifier.clickable { nav.profile(post.author.did) })
            Column(Modifier.padding(start = 12.dp).weight(1f).clickable { nav.profile(post.author.did) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    VerifiedBadge(post.author, 16.dp)
                }
                Text("@${post.author.handle}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (decision is Moderation.Decision.Hide && !revealed) {
            ContentCover(decision.reason, Modifier.padding(top = 12.dp)) { revealed = true }
            return@Column
        }
        if (decision is Moderation.Decision.Warn && !revealed) {
            ContentCover(decision.reason, Modifier.padding(top = 12.dp)) { revealed = true }
        } else {
            if (post.text.isNotBlank()) {
                Box(Modifier.padding(top = 12.dp)) {
                    RichTextView(post.text, post.facets, nav, large = true)
                }
            }
            if (decision is Moderation.Decision.BlurMedia && !revealed && (post.images.isNotEmpty() || post.card != null)) {
                ContentCover(decision.reason, Modifier.padding(top = 12.dp), media = true) { revealed = true }
            } else {
                if (post.images.isNotEmpty()) Images(post, Modifier.padding(top = 12.dp))
                post.card?.let { LinkCardView(it, Modifier.padding(top = 12.dp)) }
            }
            post.quote?.let { q -> QuoteCard(q, nav, Modifier.padding(top = 12.dp)) }
        }
        Text(
            post.createdAt?.let { fullDate(it) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        // Counts, like "12 reposts  3 quotes  45 likes".
        val counts = listOf(post.repostCount to "repost", post.quoteCount to "quote", post.likeCount to "like").filter { it.first > 0 }
        if (counts.isNotEmpty()) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                counts.forEach { (n, word) ->
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) { append(compact(n)) }
                            append(" " + word + if (n == 1) "" else "s")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
        Actions(post, nav)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

/** A quoted post in a rounded box, with its own moderation. */
@Composable
fun QuoteCard(q: Post, nav: Nav, modifier: Modifier = Modifier) {
    val prefs by Graph.store.moderation.collectAsState()
    val signedIn = Graph.store.session.collectAsState().value != null
    val quoteDecision = remember(q, prefs, signedIn) { Moderation.decide(q, prefs, signedIn) }
    if (quoteDecision is Moderation.Decision.Hide) {
        Text(
            "Quoted post hidden: ${quoteDecision.reason}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(12.dp),
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable { nav.post(q.uri) },
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(q.author.avatar, 20.dp)
                Text(q.author.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
                VerifiedBadge(q.author, 13.dp)
                Text(" @${q.author.handle} · ${shortAgo(q.createdAt)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            if (quoteDecision is Moderation.Decision.Warn) Text("Content warning: ${quoteDecision.reason}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else RichTextView(q.text, q.facets, nav, maxLines = 6)
        }
    }
}

private fun fullDate(at: java.time.Instant): String =
    java.time.format.DateTimeFormatter.ofPattern("h:mm a · d MMM yyyy").withZone(java.time.ZoneId.systemDefault()).format(at)

@Composable
private fun Images(post: Post, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    val imgs = post.images
    var viewing by remember { mutableStateOf<Int?>(null) }
    viewing?.let { ImageViewer(imgs, it) { viewing = null } }
    if (imgs.size == 1) {
        val i = imgs[0]
        // Tall photos get narrower rather than taller, so a post never grows past 520dp of photo.
        val ratio = (i.aspect ?: 1.5f).coerceIn(.5f, 3f)
        androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
            val width = minOf(maxWidth, 520.dp * ratio)
            AsyncImage(
                model = i.thumb,
                contentDescription = i.alt.ifBlank { null },
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(width).height(width / ratio).clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { viewing = 0 }.testTag("post-image"),
            )
        }
        return
    }
    // Two side by side; three as one large and two stacked; four as a 2×2 grid (read across).
    val columns = when (imgs.size) {
        2 -> listOf(listOf(0), listOf(1))
        3 -> listOf(listOf(0), listOf(1, 2))
        else -> listOf(listOf(0, 2), listOf(1, 3))
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = minOf(maxWidth * (if (imgs.size == 2) .62f else .75f), 400.dp)
        Row(Modifier.fillMaxWidth().height(height).clip(shape), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            columns.forEach { col ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    col.forEach { index ->
                        val i = imgs[index]
                        AsyncImage(
                            model = i.thumb,
                            contentDescription = i.alt.ifBlank { null },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { viewing = index }.testTag("post-image"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCardView(card: io.github.swordfishapps.social.data.LinkCard, modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { openLink(context, card.uri) },
    ) {
        Column {
            card.thumb?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1.91f)) }
            Column(Modifier.padding(12.dp)) {
                Text(Uri.parse(card.uri).host.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (card.title.isNotBlank()) Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.description.isNotBlank()) Text(card.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Reply, repost, like and share, with optimistic counts and a little pop. */
@Composable
private fun Actions(post: Post, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = Graph.store.session.collectAsState().value != null
    var likeUri by remember(post.uri) { mutableStateOf(post.myLike) }
    var likes by remember(post.uri) { mutableIntStateOf(post.likeCount) }
    var repostUri by remember(post.uri) { mutableStateOf(post.myRepost) }
    var reposts by remember(post.uri) { mutableIntStateOf(post.repostCount) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).testTag("post-actions"), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Action(Icons.Outlined.ChatBubbleOutline, post.replyCount, false, "Reply") {
            if (signedIn) nav.reply(post) else toast(context, "Sign in to reply")
        }
        Action(Icons.Rounded.Repeat, reposts, repostUri != null, "Repost") {
            if (!signedIn) { toast(context, "Sign in to repost"); return@Action }
            val was = repostUri
            if (was == null) { reposts++; repostUri = "pending" } else { reposts--; repostUri = null }
            scope.launch {
                runCatching { if (was == null) repostUri = Graph.atp.repost(post) else Graph.atp.undo(was) }
                    .onFailure { toast(context, it.message ?: "Couldn't repost") }
            }
        }
        Action(if (likeUri != null) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, likes, likeUri != null, "Like", activeColor = Color(0xFFE5486B)) {
            if (!signedIn) { toast(context, "Sign in to like"); return@Action }
            val was = likeUri
            if (was == null) { likes++; likeUri = "pending" } else { likes--; likeUri = null }
            scope.launch {
                runCatching { if (was == null) likeUri = Graph.atp.like(post) else Graph.atp.undo(was) }
                    .onFailure { toast(context, it.message ?: "Couldn't like") }
            }
        }
        IconButton(onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, webUrl(post)), "Share"))
        }) { Icon(Icons.Rounded.Share, "Share", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        PostMenu(post)
    }
}

/** More actions for a post: hide it, or mute its author. Both are saved to your account. */
@Composable
private fun PostMenu(post: Post) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = Graph.store.session.collectAsState().value != null
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Hide post") },
                leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                onClick = {
                    open = false
                    scope.launch {
                        Graph.changeModeration { it.copy(hiddenPosts = it.hiddenPosts + post.uri) }?.let { toast(context, it) }
                            ?: toast(context, "Post hidden. Undo in Settings → Moderation")
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Mute @${post.author.handle}") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.VolumeOff, null) },
                onClick = {
                    open = false
                    if (!signedIn) { toast(context, "Sign in to mute accounts"); return@DropdownMenuItem }
                    scope.launch {
                        runCatching { Graph.atp.mute(post.author.did) }
                            .onSuccess { toast(context, "Muted. Their posts are hidden behind a warning; unmute in Settings → Moderation") }
                            .onFailure { toast(context, it.message ?: "Couldn't mute") }
                    }
                },
            )
        }
    }
}

/**
 * Covers a post (or just its pictures) that a moderation rule says to warn about, with the reason
 * and a button to show it anyway.
 */
@Composable
private fun ContentCover(reason: String, modifier: Modifier = Modifier, media: Boolean = false, onShow: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(reason, style = MaterialTheme.typography.titleSmall)
                if (media) Text("The pictures are hidden", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onShow) { Text("Show") }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, count: Int, active: Boolean, label: String, activeColor: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) { pop.animateTo(1.3f, tween(100)); pop.animateTo(1f, spring(dampingRatio = .35f, stiffness = 500f)) }
    }
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, label, Modifier.size(20.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }, tint = tint)
        if (count > 0) Text(compact(count), style = MaterialTheme.typography.labelMedium, color = tint, modifier = Modifier.padding(start = 6.dp).width(36.dp))
        else Spacer(Modifier.width(42.dp))
    }
}

@Composable
fun CenterLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(52.dp)) }
}

@Composable
fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) androidx.compose.material3.FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}
