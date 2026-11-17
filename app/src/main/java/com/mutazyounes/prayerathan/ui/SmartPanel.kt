package com.mutazyounes.prayerathan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mutazyounes.prayerathan.R
import com.mutazyounes.prayerathan.smarthome.FoundLight
import com.mutazyounes.prayerathan.smarthome.SmartHome
import com.mutazyounes.prayerathan.smarthome.WizProtocol
import com.mutazyounes.prayerathan.smarthome.WizProtocol.Kind
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val TileShape = RoundedCornerShape(18.dp)
private val ChipShape = RoundedCornerShape(12.dp)
private val FieldShape = RoundedCornerShape(10.dp)
private const val POLL_MS = 15_000L

/**
 * The smart-home side of the wall: WiZ light tiles, per-light controls, and light setup.
 * Same behaviour as the MyHome TV app's Lights tab, laid out for touch.
 *
 * Tap a tile: smart toggle. Tap the sliders icon (or long-press a bulb): its controls.
 */
@Composable
fun SmartPanel(
    home: SmartHome,
    state: WallUiState,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWallPalette.current
    val lights by home.lights.collectAsState()
    val scan by home.scan.collectAsState()
    var detailId by remember { mutableStateOf<String?>(null) }

    // Re-check every device while the panel is up, so wall-switch and WiZ-app changes show too.
    LaunchedEffect(Unit) {
        while (true) {
            home.refreshAll()
            delay(POLL_MS)
        }
    }
    LaunchedEffect(editing) {
        if (!editing) home.clearScan()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // Swallow taps so nothing reaches the clock wall underneath.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        val portrait = maxHeight >= maxWidth
        val columns = if (portrait) 2 else if (maxWidth > 1100.dp) 4 else 3
        val shortest = min(maxWidth.value, maxHeight.value)
        val inset = screenInset(shortest)
        WallBackdrop(portrait = portrait)
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.backgroundDeep.copy(alpha = 0.55f)),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(inset),
        ) {
            val detail = lights.firstOrNull { it.light.id == detailId }
            PanelTopBar(
                title = when {
                    editing -> "Set up lights"
                    detail != null -> detail.light.name
                    else -> "Home controls"
                },
                time = state.albanyTime,
                amPm = state.albanyAmPm,
                next = listOf(state.nextLabel, state.countdown).filter { it.isNotBlank() }.joinToString("  "),
                showBack = detail != null && !editing,
                editing = editing,
                onBack = { detailId = null },
                onEdit = {
                    detailId = null
                    onEditingChange(!editing)
                },
                onClose = onClose,
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    editing -> EditLights(home = home, lights = lights, scan = scan)
                    detail != null -> LightControls(home = home, view = detail, portrait = portrait)
                    lights.isEmpty() -> EmptyLights(onSetUp = {
                        onEditingChange(true)
                        home.startScan()
                    })
                    else -> LightTiles(
                        home = home,
                        lights = lights,
                        columns = columns,
                        onOpen = { detailId = it },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- top bar

@Composable
private fun PanelTopBar(
    title: String,
    time: String,
    amPm: String,
    next: String,
    showBack: Boolean,
    editing: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalWallPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            RoundIconButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, description = "Back", onClick = onBack)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = labelStyle(20.sp, palette.gold),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.10.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (next.isNotEmpty()) {
                Text(
                    text = next,
                    style = tabularStyle(color = palette.prayerPast, size = 15.sp),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Text(
            text = if (amPm.isBlank()) time else "$time $amPm",
            style = tabularStyle(color = palette.clock, size = 34.sp, weight = FontWeight.Light),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        RoundIconButton(
            icon = if (editing) null else Icons.Filled.Edit,
            label = if (editing) "Done" else null,
            description = if (editing) "Done" else "Set up lights",
            onClick = onEdit,
        )
        Spacer(Modifier.width(10.dp))
        RoundIconButton(icon = Icons.Filled.Close, description = "Back to clock", onClick = onClose)
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector?,
    description: String,
    onClick: () -> Unit,
    label: String? = null,
    size: Dp = 48.dp,
) {
    val palette = LocalWallPalette.current
    Box(
        modifier = Modifier
            .height(size)
            .then(if (label == null) Modifier.width(size) else Modifier)
            .clip(CircleShape)
            .background(palette.gold.copy(alpha = 0.12f))
            .border(1.dp, palette.hairline.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (label != null) 18.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = description, tint = palette.gold, modifier = Modifier.size(24.dp))
        } else if (label != null) {
            Text(text = label, style = labelStyle(16.sp, palette.gold), fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------------- tiles

@Composable
private fun LightTiles(
    home: SmartHome,
    lights: List<SmartHome.LightView>,
    columns: Int,
    onOpen: (String) -> Unit,
) {
    val anyOn = lights.any { it.status?.reachable == true && it.status.on }
    // null = the "All" tile, shown when there are two or more devices.
    val tiles: List<SmartHome.LightView?> = if (lights.size >= 2) lights + null else lights
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { view ->
                    val mod = Modifier.weight(1f).height(168.dp)
                    if (view == null) {
                        AllTile(anyOn = anyOn, onClick = home::toggleAll, modifier = mod)
                    } else {
                        LightTile(
                            view = view,
                            onToggle = { home.toggle(view.light.id) },
                            onOpen = { onOpen(view.light.id) },
                            modifier = mod,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LightTile(
    view: SmartHome.LightView,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWallPalette.current
    val status = view.status
    val on = status?.reachable == true && status.on
    val plug = view.light.kind == Kind.Plug
    val glow = bulbColour(status) ?: palette.gold
    Box(
        modifier = modifier
            .clip(TileShape)
            .background(if (on) glow.copy(alpha = 0.16f) else palette.backgroundDeep.copy(alpha = 0.7f))
            .border(if (on) 2.dp else 1.dp, if (on) glow else palette.hairline.copy(alpha = 0.4f), TileShape)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = if (plug) null else onOpen,
            )
            .alpha(if (view.busy) 0.7f else 1f)
            .padding(18.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(if (plug) R.drawable.ic_plug else R.drawable.ic_lightbulb),
                contentDescription = null,
                tint = if (on) glow else palette.prayerPast,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = view.light.name,
                style = labelStyle(22.sp, palette.clock),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = WizProtocol.statusLine(status, view.light.kind),
                style = labelStyle(15.sp, if (status?.reachable == false) palette.batteryLow else palette.prayerPast),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!plug) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = "Controls for ${view.light.name}",
                    tint = palette.gold,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun AllTile(anyOn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWallPalette.current
    Column(
        modifier = modifier
            .clip(TileShape)
            .background(palette.gold.copy(alpha = 0.08f))
            .border(1.dp, palette.gold.copy(alpha = 0.6f), TileShape)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_power),
            contentDescription = null,
            tint = palette.gold,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(text = "All", style = labelStyle(22.sp, palette.clock), fontWeight = FontWeight.Medium)
        Text(
            text = if (anyOn) "Turn all off" else "Turn all on",
            style = labelStyle(15.sp, palette.prayerPast),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun EmptyLights(onSetUp: () -> Unit) {
    val palette = LocalWallPalette.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lightbulb),
            contentDescription = null,
            tint = palette.gold,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("No lights yet", style = labelStyle(24.sp, palette.clock))
        Text(
            text = "WiZ bulbs and plugs on your Wi-Fi, controlled directly. No cloud, no hub.",
            style = labelStyle(15.sp, palette.prayerPast),
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        PillButton(text = "Find lights", icon = Icons.Filled.Search, onClick = onSetUp)
    }
}

// ---------------------------------------------------------------------- one light's controls

@Composable
private fun LightControls(home: SmartHome, view: SmartHome.LightView, portrait: Boolean) {
    val palette = LocalWallPalette.current
    val id = view.light.id
    val kind = view.light.kind
    val status = view.status
    val on = status?.reachable == true && status.on

    var dimming by remember(id) { mutableFloatStateOf(status?.dimming?.takeIf { it > 0 }?.toFloat() ?: 100f) }
    var kelvin by remember(id) { mutableFloatStateOf(status?.temp?.takeIf { it > 0 }?.toFloat() ?: 2700f) }
    val hs = remember(id) {
        if (status != null && status.isColour) WizProtocol.rgbToHs(status.r, status.g, status.b) else Pair(30, 100)
    }
    var hue by remember(id) { mutableFloatStateOf(hs.first.toFloat()) }
    var sat by remember(id) { mutableFloatStateOf(hs.second.toFloat()) }
    var speed by remember(id) { mutableFloatStateOf(status?.speed?.takeIf { it > 0 }?.toFloat() ?: 100f) }

    // Follow the device when it changes from elsewhere, but not while a slider is held.
    LaunchedEffect(status?.dimming, status?.temp) {
        status?.dimming?.takeIf { it > 0 }?.let { dimming = it.toFloat() }
        status?.temp?.takeIf { it > 0 }?.let { kelvin = it.toFloat() }
    }

    val dim = dimming.roundToInt()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = WizProtocol.statusLine(status, kind),
                    style = labelStyle(18.sp, if (status?.reachable == false) palette.batteryLow else palette.clock),
                )
                Text(
                    text = "${kindLabel(kind)} · ${view.light.ip}",
                    style = labelStyle(13.sp, palette.prayerPast),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            PillButton(
                text = if (on) "Turn off" else "Turn on",
                painter = R.drawable.ic_power,
                filled = on,
                onClick = { home.setOn(id, !on) },
            )
        }

        if (WizProtocol.supportsBrightness(kind)) {
            ControlSection("Brightness", "$dim%") {
                GoldSlider(
                    value = dimming,
                    range = WizProtocol.MIN_DIMMING.toFloat()..100f,
                    onValueChange = { dimming = it },
                    onValueChangeFinished = { home.setBrightness(id, dimming.roundToInt()) },
                )
            }
        }

        if (WizProtocol.supportsWhite(kind)) {
            ControlSection("White", "${kelvin.roundToInt()}K") {
                ChipRows(
                    items = WizProtocol.WHITE_PRESETS,
                    perRow = if (portrait) 3 else 5,
                ) { (label, k), mod ->
                    Chip(
                        text = label,
                        selected = status?.reachable == true && status.on && status.sceneId <= 0 && status.temp == k,
                        modifier = mod,
                        onClick = {
                            kelvin = k.toFloat()
                            home.setWhite(id, k, dim)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                GoldSlider(
                    value = kelvin,
                    range = WizProtocol.MIN_KELVIN.toFloat()..WizProtocol.MAX_KELVIN.toFloat(),
                    track = Brush.horizontalGradient(listOf(Color(0xFFFFA348), Color(0xFFFFE4C2), Color(0xFFD6E6FF))),
                    onValueChange = { kelvin = it },
                    onValueChangeFinished = { home.setWhite(id, kelvin.roundToInt(), dim) },
                )
            }
        }

        if (WizProtocol.supportsColour(kind)) {
            ControlSection("Colour", null) {
                ChipRows(items = WizProtocol.COLOUR_SWATCHES, perRow = if (portrait) 6 else 12) { rgb, mod ->
                    val r = rgb shr 16 and 0xFF
                    val g = rgb shr 8 and 0xFF
                    val b = rgb and 0xFF
                    val selected = status?.isColour == true && status.on && status.r == r && status.g == g && status.b == b
                    Box(
                        modifier = mod
                            .height(48.dp)
                            .clip(ChipShape)
                            .background(Color(r, g, b))
                            .border(if (selected) 3.dp else 0.dp, if (selected) palette.clock else Color.Transparent, ChipShape)
                            .clickable {
                                val (h, s) = WizProtocol.rgbToHs(r, g, b)
                                hue = h.toFloat()
                                sat = s.toFloat()
                                home.setColour(id, r, g, b, dim)
                            },
                    )
                }
                Spacer(Modifier.height(12.dp))
                SliderLabel("Hue")
                GoldSlider(
                    value = hue,
                    range = 0f..359f,
                    track = Brush.horizontalGradient(
                        listOf(0, 60, 120, 180, 240, 300, 359).map { h ->
                            val (r, g, b) = WizProtocol.hsvToRgb(h, 100)
                            Color(r, g, b)
                        },
                    ),
                    onValueChange = { hue = it },
                    onValueChangeFinished = {
                        val (r, g, b) = WizProtocol.hsvToRgb(hue.roundToInt(), sat.roundToInt())
                        home.setColour(id, r, g, b, dim)
                    },
                )
                SliderLabel("Saturation")
                GoldSlider(
                    value = sat,
                    range = 0f..100f,
                    onValueChange = { sat = it },
                    onValueChangeFinished = {
                        val (r, g, b) = WizProtocol.hsvToRgb(hue.roundToInt(), sat.roundToInt())
                        home.setColour(id, r, g, b, dim)
                    },
                )
            }
        }

        val scenes = WizProtocol.scenesFor(kind)
        if (scenes.isNotEmpty()) {
            val activeScene = if (status?.on == true) status.sceneId else -1
            ControlSection("Scenes", activeScene.takeIf { it > 0 }?.let(WizProtocol::sceneName)) {
                ChipRows(items = scenes, perRow = if (portrait) 3 else 5) { sceneId, mod ->
                    Chip(
                        text = WizProtocol.sceneName(sceneId),
                        selected = sceneId == activeScene,
                        modifier = mod,
                        onClick = { home.setScene(id, sceneId, speed.roundToInt(), dim) },
                    )
                }
                if (activeScene > 0 && !WizProtocol.isStaticScene(activeScene)) {
                    Spacer(Modifier.height(12.dp))
                    SliderLabel("Scene speed")
                    GoldSlider(
                        value = speed,
                        range = 10f..200f,
                        onValueChange = { speed = it },
                        onValueChangeFinished = { home.setScene(id, activeScene, speed.roundToInt(), dim) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ControlSection(title: String, value: String?, content: @Composable () -> Unit) {
    val palette = LocalWallPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(palette.backgroundDeep.copy(alpha = 0.7f))
            .border(1.dp, palette.hairline.copy(alpha = 0.35f), TileShape)
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = title.uppercase(),
                style = labelStyle(14.sp, palette.gold),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.10.em,
                modifier = Modifier.weight(1f),
            )
            if (value != null) Text(value, style = tabularStyle(color = palette.clock, size = 15.sp))
        }
        content()
    }
}

@Composable
private fun SliderLabel(text: String) {
    Text(
        text = text,
        style = labelStyle(13.sp, LocalWallPalette.current.prayerPast),
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun <T> ChipRows(items: List<T>, perRow: Int, cell: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWallPalette.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(ChipShape)
            .background(if (selected) palette.gold.copy(alpha = 0.18f) else palette.background.copy(alpha = 0.6f))
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.gold else palette.hairline.copy(alpha = 0.35f), ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = labelStyle(15.sp, if (selected) palette.gold else palette.clock),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Touch slider: drag anywhere on the track. Sends one command on release, not dozens. */
@Composable
private fun GoldSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    track: Brush? = null,
) {
    val palette = LocalWallPalette.current
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val span = range.endInclusive - range.start
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(range) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun at(x: Float): Float {
                        val pad = 14.dp.toPx()
                        val fraction = ((x - pad) / (size.width - pad * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        return range.start + fraction * span
                    }
                    down.consume()
                    change(at(down.position.x))
                    drag(down.id) { c ->
                        c.consume()
                        change(at(c.position.x))
                    }
                    finished()
                }
            },
    ) {
        val thumb = 14.dp.toPx()
        val y = size.height / 2f
        val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
        val cx = thumb + fraction * (size.width - thumb * 2)
        val stroke = if (track != null) 10.dp.toPx() else 5.dp.toPx()
        drawLine(
            brush = track ?: SolidColor(palette.hairline.copy(alpha = 0.35f)),
            start = Offset(thumb, y),
            end = Offset(size.width - thumb, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (track == null) {
            drawLine(palette.gold, Offset(thumb, y), Offset(cx, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        drawCircle(palette.clock, radius = thumb, center = Offset(cx, y))
        drawCircle(palette.gold, radius = thumb - 3.dp.toPx(), center = Offset(cx, y))
    }
}

// ---------------------------------------------------------------------- setup

@Composable
private fun EditLights(home: SmartHome, lights: List<SmartHome.LightView>, scan: SmartHome.Scan) {
    val palette = LocalWallPalette.current
    var newName by remember { mutableStateOf("") }
    var newIp by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ControlSection("Your lights", lights.size.takeIf { it > 0 }?.toString()) {
            if (lights.isEmpty()) {
                Text("None yet. Find them below or add one by IP.", style = labelStyle(15.sp, palette.prayerPast))
            }
            lights.forEachIndexed { index, view ->
                var name by remember(view.light.id) { mutableStateOf(view.light.name) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextBox(
                        value = name,
                        onValueChange = {
                            name = it
                            home.rename(view.light.id, it)
                        },
                        placeholder = "Name",
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${kindLabel(view.light.kind)}\n${view.light.ip}",
                        style = labelStyle(13.sp, palette.prayerPast).copy(lineHeight = 18.sp),
                        modifier = Modifier.padding(horizontal = 12.dp).width(120.dp),
                    )
                    SmallIcon(Icons.Filled.KeyboardArrowUp, "Move up", enabled = index > 0) { home.move(view.light.id, -1) }
                    SmallIcon(Icons.Filled.KeyboardArrowDown, "Move down", enabled = index < lights.lastIndex) {
                        home.move(view.light.id, 1)
                    }
                    SmallIcon(Icons.Filled.Delete, "Remove ${view.light.name}") { home.remove(view.light.id) }
                }
            }
        }

        ControlSection("Find on this Wi-Fi", null) {
            when (scan) {
                SmartHome.Scan.Idle -> PillButton(text = "Search for WiZ lights", icon = Icons.Filled.Search, onClick = home::startScan)
                SmartHome.Scan.Running -> Text("Searching…", style = labelStyle(16.sp, palette.clock))
                is SmartHome.Scan.Done -> {
                    if (scan.found.isEmpty()) {
                        Text(
                            text = "No new lights found. Lights on a different router or network can be added by IP below.",
                            style = labelStyle(15.sp, palette.prayerPast).copy(lineHeight = 21.sp),
                        )
                    }
                    scan.found.forEach { found -> FoundRow(found) { home.add(found.suggestedName, found.ip, found.kind) } }
                    Spacer(Modifier.height(10.dp))
                    PillButton(text = "Search again", icon = Icons.Filled.Search, onClick = home::startScan)
                }
            }
        }

        ControlSection("Add by IP", null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextBox(value = newName, onValueChange = { newName = it }, placeholder = "Name", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                TextBox(
                    value = newIp,
                    onValueChange = { newIp = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                    placeholder = "192.168.1.20",
                    keyboard = KeyboardType.Uri,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                val valid = WizProtocol.isIpv4(newIp)
                PillButton(
                    text = "Add",
                    icon = Icons.Filled.Add,
                    enabled = valid,
                    onClick = {
                        home.add(newName, newIp)
                        newName = ""
                        newIp = ""
                    },
                )
            }
            Text(
                text = "Give each light a fixed address (DHCP reservation) in your router, and turn on " +
                    "\"Allow local communication\" in the WiZ app.",
                style = labelStyle(13.sp, palette.prayerPast).copy(lineHeight = 19.sp),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun FoundRow(found: FoundLight, onAdd: () -> Unit) {
    val palette = LocalWallPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (found.kind == Kind.Plug) R.drawable.ic_plug else R.drawable.ic_lightbulb),
            contentDescription = null,
            tint = palette.gold,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = "${kindLabel(found.kind)} · ${found.ip}",
            style = labelStyle(16.sp, palette.clock),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        PillButton(text = "Add", icon = Icons.Filled.Add, onClick = onAdd)
    }
}

@Composable
private fun TextBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val palette = LocalWallPalette.current
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(FieldShape)
            .background(palette.background.copy(alpha = 0.7f))
            .border(1.dp, palette.hairline.copy(alpha = 0.45f), FieldShape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = labelStyle(16.sp, palette.prayerPast.copy(alpha = 0.6f)))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.clock, fontSize = 16.sp, fontFamily = EnglishFontFamily),
            cursorBrush = SolidColor(palette.gold),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalWallPalette.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.3f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = palette.gold, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    painter: Int? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalWallPalette.current
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(if (filled) palette.gold else palette.gold.copy(alpha = 0.12f))
            .border(1.dp, palette.gold.copy(alpha = 0.7f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (filled) palette.settingsPanel else palette.gold
        if (icon != null) Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        if (painter != null) Icon(painter = painterResource(painter), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        if (icon != null || painter != null) Spacer(Modifier.width(8.dp))
        Text(text = text, style = labelStyle(16.sp, tint), fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------- helpers

private fun kindLabel(kind: Kind): String = when (kind) {
    Kind.Plug -> "Smart plug"
    Kind.Rgb -> "Colour bulb"
    Kind.Tunable -> "White bulb"
    Kind.Dimmable -> "Dimmable bulb"
    Kind.Unknown -> "WiZ device"
}

/** The colour a lit bulb is showing, for the tile glow. Null = use gold. */
private fun bulbColour(status: WizProtocol.Status?): Color? {
    if (status == null || !status.reachable || !status.on) return null
    if (status.isColour) return Color(status.r, status.g, status.b)
    return null
}

/** Small lightbulb button for the wall's corner, beside settings. */
@Composable
fun LightsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalWallPalette.current
    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lightbulb),
            contentDescription = "Home controls",
            tint = palette.gold,
            modifier = Modifier.size(20.dp),
        )
    }
}
