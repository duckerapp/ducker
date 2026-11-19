package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.glassPress
import com.github.kr328.clash.ui.components.rememberGlassPress
import com.github.kr328.clash.ui.components.sheetSurface
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.common.util.providerLink
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.components.BrandAvatar
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.formatCount
import com.github.kr328.clash.ui.components.named
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * The bell on «Подписки» (stage 15), there while some subscription has announcements. With one not seen yet it turns
 * into a filled pill with «Новое», so the mark is a word and not a dot alone.
 */
@Composable
internal fun AnnouncementsBell(fresh: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (fresh) scheme.onPrimary else scheme.onSurface

    // One node in both looks, as [HeaderButton] when nothing is new: opening the sheet marks all seen, and a button
    // swapped for another while it was open left the remote, once the sheet closed, on the card below.
    Row(
        modifier = Modifier.minimumInteractiveComponentSize()
            .height(44.dp)
            .widthIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (fresh) scheme.primary else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (fresh) 12.dp else 0.dp, end = if (fresh) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Symbols.Notifications, contentDescription = stringResource(R.string.announcements_bell), tint = tint)

        if (fresh) {
            Text(stringResource(R.string.announcement_new), style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

/** One subscription's part of the sheet: whose announcements they are, the newest first. */
internal data class AnnouncementGroup(val profile: Profile, val favorite: Boolean, val items: List<AnnouncementItem>)

internal data class AnnouncementItem(val text: String, val at: Long, val known: Boolean, val fresh: Boolean)

/**
 * The sheet's groups — [profile]'s alone, or every subscription's for the bell — in the order «Подписки» lists them: the
 * active one, the favourites, the rest. A fixed order, not the newest first: the owner, 05.11.2026, found the groups
 * «в разнобой». Taken when the sheet opens: opening marks them seen, and «Новое» stays on them while it is open.
 */
internal fun SubscriptionsState.announcementGroups(profile: UUID?): List<AnnouncementGroup> {
    val byNumber = announcements.groupBy { it.profile }

    return profiles
        .filter { profile == null || it.uuid == profile }
        .mapNotNull { owner ->
            val list = byNumber[owner.uuid.toString()] ?: return@mapNotNull null

            AnnouncementGroup(
                profile = owner,
                favorite = owner.uuid in favorites,
                items = list.sortedByDescending { it.at }.map { AnnouncementItem(it.text, it.at, it.known, fresh(it)) },
            )
        }
}

/**
 * What a group shows before «Ещё»: with several subscriptions in the sheet, the newest [SHOWN] and every one not seen
 * yet, so a long history of the active subscription does not push the others' news off the screen. One subscription's
 * own history is shown whole.
 */
internal fun AnnouncementGroup.shown(collapse: Boolean): List<AnnouncementItem> =
    if (!collapse) items else items.filterIndexed { index, item -> index < SHOWN || item.fresh }

private const val SHOWN = 3

/**
 * The providers' announcements of the last ten days (stage 15): from a subscription's “⋯” its own, from the bell on
 * «Подписки» all of them. Each subscription under its logo and name, so it is plain which provider said what; links
 * open the way «Поддержка» does.
 */
@Composable
internal fun AnnouncementsSheet(
    open: Boolean,
    groups: List<AnnouncementGroup>,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.announcements),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        val collapse = groups.size > 1

        groups.forEach { group ->
            key(group.profile.uuid) {
                var expanded by remember { mutableStateOf(false) }
                val shown = if (expanded) group.items else group.shown(collapse)
                val hidden = group.items.size - shown.size

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupHeader(group.profile, group.favorite)

                    // One backing with inset lines, as the subscription's addresses (D-326). On the pane glass it swells
                    // under a touch its links and «more» do not take, as Home's cards (the owner, 19.11: no life in it).
                    val press = rememberGlassPress()
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .glassPress(press, own = false)
                            .sheetSurface(RoundedCornerShape(20.dp), press)
                            .padding(vertical = 4.dp),
                    ) {
                        shown.forEachIndexed { index, item ->
                            if (index > 0) Divider()

                            AnnouncementRow(item, onOpenLink)
                        }

                        if (hidden > 0) {
                            Divider()
                            MoreRow(hidden) { expanded = true }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

/** «Ещё 4» under a collapsed group: the older announcements of that subscription, in place. */
@Composable
private fun MoreRow(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.announcements_more, formatCount(count)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(Symbols.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/**
 * Whose announcements: the logo and the shown name with a star for a favourite, as on «Подписки»; under it «Активна»
 * for the active subscription and the provider's brand when the person named the subscription themselves.
 */
@Composable
private fun GroupHeader(profile: Profile, favorite: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandAvatar(profile.brandLogo, Modifier.size(32.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = profile.shownName.asName(),
                    style = MaterialTheme.typography.titleSmall.named(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (favorite) {
                    Icon(Symbols.Star, contentDescription = stringResource(R.string.favorite_marked), modifier = Modifier.size(14.dp))
                }
            }

            // The person's own name for the subscription says nothing of the provider: its brand under it.
            val brand = (profile.brandName.ifEmpty { profile.profileTitle })
                .takeIf { it.isNotEmpty() && !it.equals(profile.shownName, ignoreCase = true) }
            val line = listOfNotNull(stringResource(R.string.ducker_active).takeIf { profile.active }, brand?.asName())

            if (line.isNotEmpty()) {
                Text(
                    text = line.joinToString(stringResource(R.string.separator)),
                    style = MaterialTheme.typography.bodySmall.named(),
                    color = if (profile.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AnnouncementRow(item: AnnouncementItem, onOpenLink: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))

    // On a TV each announcement takes the remote's frame, and the sheet scrolls to it.
    Column(
        modifier = Modifier.fillMaxWidth()
            .then(if (LocalTv.current) Modifier.focusable() else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!item.known || item.fresh) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // A text kept from before the history has no date of its own.
                if (!item.known) {
                    Text(
                        text = announcedAt(item.at),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }

                if (item.fresh) {
                    Text(
                        text = stringResource(R.string.announcement_new),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onPrimary,
                        modifier = Modifier.clip(CircleShape).background(scheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        SelectionContainer {
            Text(
                text = linked(item.text, linkStyle, onOpenLink),
                style = MaterialTheme.typography.bodyMedium.named(),
            )
        }
    }
}

/**
 * «5 нояб., 14:30» in the app's language: `DateUtils` takes the system's, and on a phone with Persian chosen for ducker
 * alone the date stayed «Nov 5».
 */
@Composable
private fun announcedAt(at: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(LocalContext.current)) "dMMMHm" else "dMMMhm"

    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(at))
}

private fun linked(text: String, style: TextLinkStyles, onOpen: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var at = 0

    for ((range, link) in announcementLinks(text)) {
        append(text, at, range.first)
        withLink(LinkAnnotation.Clickable(link, style) { onOpen(link) }) { append(text, range.first, range.last + 1) }
        at = range.last + 1
    }

    append(text, at, text.length)
}

/**
 * The links of an announcement that open, with where they stand (audit of 30.09, 9б.9): only what [providerLink] lets
 * through, the rule of «Поддержка», and without the sentence's closing mark.
 */
internal fun announcementLinks(text: String): List<Pair<IntRange, String>> = LINK.findAll(text).mapNotNull { match ->
    val raw = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '»', '"', '\'')
    val link = providerLink(raw) ?: return@mapNotNull null

    IntRange(match.range.first, match.range.first + raw.length - 1) to link
}.toList()

private val LINK = Regex("""(?i)\b(?:https?|tg)://[^\s<>"«»]+""")
