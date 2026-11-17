package com.mutazyounes.prayerathan.ui

import android.app.Activity
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mutazyounes.prayerathan.engine.CityCatalog
import com.mutazyounes.prayerathan.shell.BatteryLowAlert
import com.mutazyounes.prayerathan.shell.BatteryMonitor
import com.mutazyounes.prayerathan.smarthome.SmartHome
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Trial: Albany plus countdown in StackedClockWall.kt. No Jordan clock.
 * Set this false to restore the split layout in this file.
 */
const val UseStackedClocks = true

/** Set while the smart-home panel is enabled: opens it from the wall's corner button. */
val LocalOpenLights = compositionLocalOf<(() -> Unit)?> { null }

/** How long the smart-home panel may sit untouched before the clock comes back. */
const val SMART_PANEL_IDLE_MS = 30_000L

/** Longer while setting up lights, since typing on the keyboard isn't seen as a touch here. */
const val SMART_PANEL_SETUP_IDLE_MS = 120_000L

@Composable
fun WallScreen(
    viewModel: WallViewModel,
    smartHome: SmartHome,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var userAwake by remember { mutableStateOf(false) }

    val panelEnabled by smartHome.enabled.collectAsState()
    var panelOpen by remember { mutableStateOf(false) }
    var panelEditing by remember { mutableStateOf(false) }
    var lastPanelTouch by remember { mutableLongStateOf(0L) }
    val openPanel = {
        lastPanelTouch = SystemClock.uptimeMillis()
        panelOpen = true
    }
    val closePanel = {
        panelOpen = false
        panelEditing = false
    }

    LaunchedEffect(userAwake) {
        if (userAwake) {
            delay(15_000L)
            userAwake = false
        }
    }

    // Idle return: every touch on the panel restarts the countdown.
    LaunchedEffect(panelOpen, panelEditing, lastPanelTouch) {
        if (panelOpen) {
            delay(if (panelEditing) SMART_PANEL_SETUP_IDLE_MS else SMART_PANEL_IDLE_MS)
            closePanel()
        }
    }
    // The Athan always wins: back to the clock the moment it starts.
    LaunchedEffect(state.athanPlaying) {
        if (state.athanPlaying) closePanel()
    }
    LaunchedEffect(panelEnabled) {
        if (!panelEnabled) closePanel()
    }

    val flip by animateFloatAsState(
        targetValue = if (panelOpen) 180f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "wallFlip",
    )
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 96.dp.toPx() }

    val showBlackout = state.isNightBlackout && !userAwake && !settingsOpen && !panelOpen

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val assets = context.assets
        withContext(Dispatchers.IO) {
            CityCatalog.loadBundled { name ->
                assets.open(name).bufferedReader().use { it.readText() }
            }
        }
    }
    DisposableEffect(showBlackout) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val params = window.attributes
            params.screenBrightness = when {
                showBlackout -> 0.0f
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = params
        }
        onDispose {
            val window = (context as? Activity)?.window
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    CompositionLocalProvider(
        LocalWallPalette provides DarkWallPalette,
        LocalOpenLights provides if (panelEnabled) openPanel else null,
    ) {
        Box(modifier.fillMaxSize()) {
            // A card flip: the clock is the front, the smart-home panel the back.
            if (flip < 90f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = flip
                            cameraDistance = 14f * density.density
                        }
                        .pointerInput(panelEnabled) {
                            if (!panelEnabled) return@pointerInput
                            var travel = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { travel = 0f },
                                onDragEnd = { if (abs(travel) > swipeThreshold) openPanel() },
                            ) { change, dx ->
                                change.consume()
                                travel += dx
                            }
                        },
                ) {
                    WallScreen(
                        state = state,
                        onStopAthan = viewModel::stopAthan,
                        onOpenSettings = { settingsOpen = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationY = flip - 180f
                            cameraDistance = 14f * density.density
                        }
                        // Watches every touch without taking it, to restart the idle countdown.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    lastPanelTouch = SystemClock.uptimeMillis()
                                }
                            }
                        },
                ) {
                    SmartPanel(
                        home = smartHome,
                        state = state,
                        editing = panelEditing,
                        onEditingChange = { panelEditing = it },
                        onClose = closePanel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (showBlackout) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { userAwake = true },
                        ),
                )
            }
            if (settingsOpen) {
                SettingsSheet(
                    cityLabel = state.locationCity,
                    latitude = state.locationLatitude,
                    longitude = state.locationLongitude,
                    locationError = state.locationError,
                    athanSoundId = state.athanSoundId,
                    customAthanTitle = state.customAthanTitle,
                    athkarEnabled = state.athkarEnabled,
                    athkarVolume = state.athkarVolume,
                    morningAthkarEnabled = state.morningAthkarEnabled,
                    morningAthkarVolume = state.morningAthkarVolume,
                    mutedPrayers = state.mutedPrayers,
                    prayerVolumes = state.prayerVolumes,
                    prayerOffsets = state.prayerOffsets,
                    prayerClockTimes = state.prayerClockTimes,
                    methodSettings = state.methodSettings,
                    resolvedMethod = state.resolvedMethod,
                    nightBlackoutEnabled = state.nightBlackoutEnabled,
                    smartPanelEnabled = panelEnabled,
                    demoId = state.demoId,
                    onSelectLocation = { label, lat, lon, zone ->
                        if (viewModel.saveLocation(label, lat, lon, zone)) {
                            settingsOpen = false
                        }
                    },
                    onUseGps = viewModel::useGps,
                    onSelectAthanSound = viewModel::setAthanSound,
                    onCustomAthanImported = viewModel::useCustomAthan,
                    onAthkarEnabledChange = viewModel::setAthkarEnabled,
                    onAthkarVolumeChange = viewModel::setAthkarVolume,
                    onPlayAthkarVolumePreview = viewModel::playAthkarVolumePreview,
                    onMorningAthkarEnabledChange = viewModel::setMorningAthkarEnabled,
                    onMorningAthkarVolumeChange = viewModel::setMorningAthkarVolume,
                    onPlayMorningAthkarVolumePreview = viewModel::playMorningAthkarVolumePreview,
                    onTogglePrayerMute = viewModel::togglePrayerMute,
                    onPrayerVolumeChange = viewModel::setPrayerVolume,
                    onPrayerOffsetChange = viewModel::setPrayerOffset,
                    onCalcMethodChange = viewModel::setCalcMethod,
                    onAsrSchoolChange = viewModel::setAsrSchool,
                    onPlayPrayerVolumePreview = viewModel::playPrayerVolumePreview,
                    onNightBlackoutChange = viewModel::setNightBlackout,
                    onSmartPanelEnabledChange = smartHome::setEnabled,
                    onPlayAthanDemo = viewModel::playAthanDemo,
                    onDismiss = {
                        viewModel.stopDemo()
                        settingsOpen = false
                    },
                )
            }
        }
    }
}

@Composable
fun WallScreen(
    state: WallUiState,
    onStopAthan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalWallPalette provides DarkWallPalette) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (state.athanPlaying || state.athkarPlaying) {
                            onStopAthan()
                        }
                    },
                    onLongClick = onOpenSettings,
                ),
        ) {
            val shortest = min(maxWidth.value, maxHeight.value)
            val type = typeScale(shortest)
            val inset = screenInset(shortest)
            val portrait = maxHeight >= maxWidth
            WallBackdrop(portrait = portrait)
            if (UseStackedClocks) {
                if (portrait) {
                    PortraitStackedWall(state, type, inset, onOpenSettings)
                } else {
                    LandscapeStackedWall(state, type, inset, onOpenSettings)
                }
            } else if (portrait) {
                PortraitWall(state, type, inset, onOpenSettings)
            } else {
                LandscapeWall(state, type, inset, onOpenSettings)
            }
        }
    }
}

@Composable
private fun PortraitWall(
    state: WallUiState,
    type: TypeScale,
    inset: Dp,
    onOpenSettings: () -> Unit,
) {
    val (hours, minutes, seconds) = countdownParts(state.countdown)
    val playingName = state.playingName?.englishLabel()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = inset, end = inset, bottom = inset),
        ) {
            Spacer(Modifier.weight(7f))
            ClockBlock(
                label = "",
                hourMinute = state.albanyTime,
                amPm = state.albanyAmPm,
                emphasis = ClockEmphasis.Local,
                showStar = true,
                type = type,
                portrait = true,
                modifier = Modifier
                    .weight(42f)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.weight(2f))
            CountdownBlock(
                label = state.nextLabel,
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                playingPrayerName = playingName,
                type = type,
                countdownSize = type.countdownPortrait,
                portrait = true,
                athkarCaption = state.athkarCaption,
                modifier = Modifier
                    .weight(20f)
                    .fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))
            HorizontalHairline()
            PrayerGrid(
                cells = state.cells,
                portrait = true,
                type = type,
                countdown = state.countdown,
                athanPlaying = state.athanPlaying,
                playingName = playingName,
                modifier = Modifier
                    .weight(28f)
                    .fillMaxWidth(),
            )
        }
        Header(
            location = state.locationLabel,
            weekday = state.weekday,
            dateLine = state.gregorianDate,
            type = type,
            onOpenSettings = onOpenSettings,
            weatherLine = state.weatherLine,
            showBattery = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(inset),
        )
    }
}

@Composable
private fun LandscapeWall(
    state: WallUiState,
    type: TypeScale,
    inset: Dp,
    onOpenSettings: () -> Unit,
) {
    val (hours, minutes, seconds) = countdownParts(state.countdown)
    val playingName = state.playingName?.englishLabel()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = inset),
    ) {
        Box(
            modifier = Modifier
                .weight(70f)
                .fillMaxWidth(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columnWidth = maxWidth / 2
                val starDiameter = columnWidth * 0.70f
                val headerClearance = maxHeight * 0.16f
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerClearance),
                ) {
                    ClockBlock(
                        label = "",
                        hourMinute = state.albanyTime,
                        amPm = state.albanyAmPm,
                        emphasis = ClockEmphasis.Local,
                        showStar = true,
                        type = type,
                        portrait = false,
                        starDiameter = starDiameter,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    CountdownBlock(
                        label = state.nextLabel,
                        hours = hours,
                        minutes = minutes,
                        seconds = seconds,
                        playingPrayerName = playingName,
                        type = type,
                        countdownSize = type.countdownLandscape,
                        portrait = false,
                        athkarCaption = state.athkarCaption,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = headerClearance),
                ) {
                    Spacer(Modifier.weight(1f))
                    VerticalHairline()
                    Spacer(Modifier.weight(1f))
                }
            }
            Header(
                location = state.locationLabel,
                weekday = state.weekday,
                dateLine = state.gregorianDate,
                type = type,
                onOpenSettings = onOpenSettings,
                weatherLine = state.weatherLine,
                leftWeight = 1f,
                rightWeight = 1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = inset)
                    .align(Alignment.TopCenter),
            )
        }
        HorizontalHairline()
        PrayerGrid(
            cells = state.cells,
            portrait = false,
            type = type,
            countdown = state.countdown,
            athanPlaying = state.athanPlaying,
            playingName = playingName,
            modifier = Modifier
                .weight(30f)
                .fillMaxWidth(),
        )
    }
}

@Composable
fun Header(
    location: String,
    weekday: String,
    dateLine: String,
    type: TypeScale,
    onOpenSettings: () -> Unit,
    weatherLine: String = "",
    showBattery: Boolean = false,
    leftWeight: Float? = null,
    rightWeight: Float? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWallPalette.current
    val split = leftWeight != null && rightWeight != null
    val weatherMaxSp = when {
        showBattery -> type.dateLine.value * 1.35f
        split -> type.label.value * 1.22f
        else -> type.dateLine.value * 1.35f
    }
    val weatherMinSp = if (showBattery) type.label.value * 1.0f else type.label.value * 0.95f
    val dateMaxSp = when {
        showBattery -> type.dateLine.value * 1.45f
        split -> type.dateLine.value * 1.15f
        else -> type.dateLine.value
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(leftWeight ?: 1f)
                .fillMaxWidth(),
        ) {
            if (weatherLine.isNotEmpty()) {
                val iconRes = weatherIconRes(weatherLine)
                WeatherRow(
                    text = weatherLine,
                    iconRes = iconRes,
                    color = palette.gold,
                    maxSp = weatherMaxSp,
                    minSp = weatherMinSp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = location,
                style = tabularStyle(
                    color = palette.location,
                    size = type.location,
                    weight = FontWeight.Medium,
                ).copy(letterSpacing = 0.12.em),
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (weatherLine.isNotEmpty()) 2.dp else 0.dp),
            )
            if (showBattery) {
                BatteryChip(
                    normalColor = palette.gold,
                    lowColor = palette.batteryLow,
                    textSize = type.label * 0.82f,
                    diameter = 54.dp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Column(
            // Always weighted: an unweighted Row child that fills to max width
            // claims the Row's full incoming width, starving the location
            // column on the left. That was why location/weather vanished
            // in portrait, where this column previously had no weight.
            modifier = Modifier.weight(rightWeight ?: 1f).fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            FittingLine(
                text = "$weekday  $dateLine",
                color = palette.date,
                weight = FontWeight.Normal,
                align = TextAlign.End,
                maxSp = dateMaxSp,
                minSp = if (showBattery) type.label.value * 1.1f else type.label.value,
                modifier = Modifier.fillMaxWidth(),
            )
            WallCornerButtons(
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun rememberBatteryPercent(): Int {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(BatteryMonitor.stickyPercent(context)) }
    DisposableEffect(context) {
        val receiver = BatteryMonitor.register(context) {
            percent = it
            BatteryLowAlert.onPercent(it)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return percent
}

@Composable
private fun BatteryChip(
    normalColor: Color,
    lowColor: Color,
    textSize: androidx.compose.ui.unit.TextUnit,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val percent = rememberBatteryPercent()
    if (percent < 0) return
    val low = percent < BatteryLowAlert.LOW_THRESHOLD
    val color = if (low) lowColor else normalColor
    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        BatteryCircle(
            percent = percent,
            color = color,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "$percent%",
            style = tabularStyle(
                color = color,
                size = textSize,
                weight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun BatteryCircle(
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 3.5.dp.toPx()
        val diameter = min(size.width, size.height) - strokeWidth
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = color.copy(alpha = 0.28f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        val sweep = 360f * (percent.coerceIn(0, 100) / 100f)
        if (sweep > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun WeatherRow(
    text: String,
    iconRes: Int,
    color: Color,
    maxSp: Float,
    minSp: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val fitted = remember(text, maxWidth, maxSp, minSp, density) {
            if (maxWidth.value <= 0f || text.isEmpty()) {
                minSp
            } else {
                val referenceSize = 100f
                val referenceStyle = tabularStyle(
                    color = color,
                    size = referenceSize.sp,
                    weight = FontWeight.Medium,
                )
                val referenceTextWidth = with(density) {
                    textMeasurer.measure(text, referenceStyle).size.width.toDp().value
                }
                // Icon size is ~1.15x line height, plus 6dp gap.
                // At referenceSize 100sp, icon is ~115dp + 6dp gap = 121dp.
                val referenceTotalWidth = referenceTextWidth + 121f
                val byWidth = if (referenceTotalWidth > 0f) {
                    referenceSize * maxWidth.value / referenceTotalWidth
                } else {
                    maxSp
                }
                byWidth.coerceIn(minSp, maxSp)
            }
        }
        val iconSizeDp = with(density) { (fitted * 1.05f).sp.toDp() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(iconSizeDp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = text,
                style = tabularStyle(
                    color = color,
                    size = fitted.sp,
                    weight = FontWeight.Medium,
                ),
                textAlign = TextAlign.Start,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FittingLine(
    text: String,
    color: Color,
    weight: FontWeight,
    align: TextAlign,
    maxSp: Float,
    minSp: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        // Measure the real string instead of guessing an em-width-per-char
        // ratio. The guess ran wide for this font and let text overflow
        // its box uncaught, since Text() clips a maxLines=1 overrun silently.
        val fitted = remember(text, maxWidth, maxSp, minSp, weight, density) {
            if (maxWidth.value <= 0f || text.isEmpty()) {
                minSp
            } else {
                val referenceSize = 100f
                val referenceStyle = tabularStyle(
                    color = color,
                    size = referenceSize.sp,
                    weight = weight,
                )
                val referenceWidth = with(density) {
                    textMeasurer.measure(text, referenceStyle).size.width.toDp().value
                }
                val byWidth = if (referenceWidth > 0f) {
                    referenceSize * maxWidth.value / referenceWidth
                } else {
                    maxSp
                }
                byWidth.coerceIn(minSp, maxSp)
            }
        }
        Text(
            text = text,
            style = tabularStyle(
                color = color,
                size = fitted.sp,
                weight = weight,
            ),
            textAlign = align,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The wall's corner controls: lights (when the smart-home panel is on) and settings. */
@Composable
fun WallCornerButtons(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLights = LocalOpenLights.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (openLights != null) {
            LightsButton(onClick = openLights)
        }
        SettingsButton(onClick = onOpenSettings, compact = true)
    }
}

@Composable
fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val palette = LocalWallPalette.current
    val boxSize = if (compact) 36.dp else 48.dp
    val iconSize = if (compact) 18.dp else 22.dp
    Box(
        modifier = modifier
            .size(boxSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = palette.gold,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun StarWatermark(modifier: Modifier = Modifier) {
    val star = LocalWallPalette.current.star
    Canvas(modifier) {
        val stroke = HairlineWidth.toPx()
        val side = min(size.width, size.height) * 0.58f
        val topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
        val rect = Size(side, side)
        drawRect(
            color = star,
            topLeft = topLeft,
            size = rect,
            style = Stroke(width = stroke),
        )
        rotate(45f) {
            drawRect(
                color = star,
                topLeft = topLeft,
                size = rect,
                style = Stroke(width = stroke),
            )
        }
    }
}

internal fun countdownParts(countdown: String): Triple<String, String, String> {
    val parts = countdown.split(":")
    return Triple(
        parts.getOrElse(0) { "00" },
        parts.getOrElse(1) { "00" },
        parts.getOrElse(2) { "00" },
    )
}
