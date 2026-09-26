package com.ivy.planner.ui.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.ivy.navigation.PlannerEditScreen
import com.ivy.navigation.PlannerEntryViewScreen
import com.ivy.navigation.PlannerTimelineScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.planner.data.LibraryRepository
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.Markdown
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.ImportanceNames
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.RichText
import com.ivy.planner.ui.SectionLabel
import com.ivy.planner.ui.importanceColor
import com.ivy.planner.ui.journal.PersonAvatar
import com.ivy.planner.ui.label
import com.ivy.planner.ui.openAttachment
import com.ivy.planner.ui.swipeDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class EntryViewModel @Inject constructor(
    val library: LibraryRepository,
    val planner: PlannerRepository,
    prefs: PlannerPrefs,
) : ViewModel() {
    init {
        ImportanceNames.labels = prefs.importanceLabels
    }
}

@Composable
fun PlannerEntryViewScreenImpl(screen: PlannerEntryViewScreen) {
    val vm: EntryViewModel = screenScopedViewModel()
    PlannerTheme { EntryViewUi(vm, screen.entryId) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryViewUi(vm: EntryViewModel, startId: String) {
    val nav = navigation()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // stepping to next / previous changes this; Back still returns to where you came from
    var currentId by remember { mutableStateOf(startId) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    val lib by remember { vm.library.observe() }.collectAsState(initial = null)
    val l = lib ?: return
    val e = l.entries.firstOrNull { it.id == currentId } ?: return
    val photos = l.photosOf[e.id].orEmpty().map(vm.library::photoFile)
    val files = l.filesOf[e.id].orEmpty()
    val people = l.peopleOf[e.id].orEmpty().mapNotNull { l.person(it) }
    val collections = l.collectionsOf[e.id].orEmpty().mapNotNull { l.collection(it) }
    // chronological neighbours of the same kind
    val siblings = l.entries.filter { it.kind == e.kind && it.date != null }
        .sortedWith(compareBy<Entry>({ it.date }, { it.time }, { it.createdAt }))
    val index = siblings.indexOfFirst { it.id == e.id }
    val previous = siblings.getOrNull(index - 1)
    val next = siblings.getOrNull(index + 1)
    val sameDay = e.date?.let { d ->
        l.entries.filter { o ->
            o.id != e.id && o.kind == EntryKind.JOURNAL && o.date?.let { it.monthValue == d.monthValue && it.dayOfMonth == d.dayOfMonth && it.year != d.year } == true
        }.sortedByDescending { it.date }
    }.orEmpty()
    val accent = if (e.kind == EntryKind.JOURNAL) importanceColor(e.importance) else PlannerColors.Done

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .swipeDays(key = e.id, onPrevious = { previous?.let { currentId = it.id } }, onNext = { next?.let { currentId = it.id } }),
            ) {
                // cover: the first photo, swipe for the others; or a soft colour band
                Box {
                    if (photos.isNotEmpty()) {
                        CoverPager(photos, onOpen = { viewer = it })
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent))),
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp).size(if (e.kind == EntryKind.JOURNAL) 44.dp else 0.dp),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        RoundButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                        RoundButton(onClick = {
                            nav.navigateTo(PlannerEditScreen(entryId = e.id, epochDay = e.date?.toEpochDay(), kind = e.kind.name))
                        }) { Icon(Icons.Filled.Edit, "Edit", tint = Color.White) }
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (e.kind == EntryKind.JOURNAL && e.importance > 0) {
                        Text(
                            "★ " + (ImportanceNames.label(e.importance) ?: ""),
                            Modifier.clip(RoundedCornerShape(12.dp)).background(accent).padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B1D1B),
                        )
                    }
                    Text(e.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 31.sp)
                    e.date?.let { d ->
                        val years = LocalDate.now().year - d.year
                        Text(
                            listOfNotNull(
                                d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + d.dayOfMonth + " " +
                                    d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + d.year,
                                "W${d.isoWeek().week}",
                                e.time?.label(),
                                when {
                                    years == 1 -> "1 year ago"
                                    years > 1 -> "$years years ago"
                                    else -> null
                                },
                            ).joinToString(" · "),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (people.isNotEmpty() || collections.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            people.forEach { p ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { nav.navigateTo(PlannerTimelineScreen(personId = p.id)) }
                                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PersonAvatar(p.name, size = 28, photo = p.photoFile?.let(vm.library::photoFile))
                                    Spacer(Modifier.width(8.dp))
                                    Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            collections.forEach { c ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { nav.navigateTo(PlannerTimelineScreen(collectionId = c.id)) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(c.color)))
                                    Spacer(Modifier.width(6.dp))
                                    Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    if (e.description.isNotBlank()) {
                        RichText(
                            markdown = e.description,
                            modifier = Modifier.padding(top = 4.dp),
                            onToggleCheck = { line ->
                                scope.launch { vm.planner.setDescription(e.id, Markdown.toggleCheck(e.description, line)) }
                            },
                        )
                    }
                    if (photos.size > 1) {
                        SectionLabel("Photos", MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 3) {
                            photos.drop(1).forEachIndexed { i, f ->
                                AsyncImage(
                                    model = f,
                                    contentDescription = "Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { viewer = i + 1 },
                                )
                            }
                        }
                    }
                    files.forEach { (name, display) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { openAttachment(context, vm.library.photoFile(name), "application/pdf") }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = PlannerColors.Accent)
                            Spacer(Modifier.width(10.dp))
                            Text(display, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (sameDay.isNotEmpty()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SectionLabel("Also on this day")
                            sameDay.forEach { o ->
                                Text(
                                    "${o.date?.year} · ${o.title}",
                                    Modifier.fillMaxWidth().clickable { currentId = o.id }.padding(vertical = 2.dp),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            // previous and next memory, with where you'll land
            if (previous != null || next != null) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NeighbourButton(previous, isNext = false, modifier = Modifier.weight(1f)) { previous?.let { currentId = it.id } }
                    Spacer(Modifier.width(8.dp))
                    NeighbourButton(next, isNext = true, modifier = Modifier.weight(1f)) { next?.let { currentId = it.id } }
                }
            }
        }
    }
    viewer?.let { start -> PhotoViewer(photos, start) { viewer = null } }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun NeighbourButton(e: Entry?, isNext: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = e != null, onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = if (isNext) Alignment.End else Alignment.Start,
    ) {
        if (e != null) {
            Text(
                (if (isNext) "" else "‹ ") + e.title + (if (isNext) " ›" else ""),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                e.date?.let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${it.year}" } ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The cover: photos side by side, "1 / 4" in the corner; tap for full screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverPager(photos: List<File>, onOpen: (Int) -> Unit) {
    val pager = rememberPagerState(pageCount = { photos.size })
    Box {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(300.dp)) { page ->
            AsyncImage(
                model = photos[page],
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { onOpen(page) },
            )
        }
        if (photos.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${photos.size}",
                Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 9.dp, vertical = 4.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Full-screen photos: swipe between them, pinch or double-drag to zoom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoViewer(photos: List<File>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val pager = rememberPagerState(initialPage = start.coerceIn(0, (photos.size - 1).coerceAtLeast(0)), pageCount = { photos.size })
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offset by remember(page) { mutableStateOf(Offset.Zero) }
                AsyncImage(
                    model = photos[page],
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            // two fingers zoom; one finger pans only when zoomed, otherwise the swipe changes photo
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed >= 2 || scale > 1f) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset = if (scale == 1f) Offset.Zero else offset + pan
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
        }
    }
}
