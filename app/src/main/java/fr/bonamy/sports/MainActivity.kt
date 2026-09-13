package fr.bonamy.sports

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import fr.bonamy.sports.core.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

@UnstableApi
class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, MORE, SCHEDULE, CHANNELS, PLAYER }
    private var screen = Screen.HOME
    private var sport = Sport.FOOTBALL
    private var homeFocus = "FOOTBALL"
    private val repository = SportsRepository()
    private val resolver = StreamResolver(trace = { android.util.Log.d("SportsSource", it) })
    private val schedules = mutableMapOf<Sport, List<SportsEvent>>()
    private var selectedEvent: SportsEvent? = null
    private var selectedChannel: Channel? = null
    private var streamOptions = emptyList<StreamLink>()
    private var streamIndex = 0
    private var browseJob: Job? = null
    private var tickerJob: Job? = null
    private var resolveJob: Job? = null
    private var discoveryJob: Job? = null
    private var renewalJob: Job? = null
    private var recoveryJob: Job? = null
    private var stableJob: Job? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playerChrome: PlayerChrome? = null
    private var retries = 0
    private var focusedEventId: String? = null
    private var scheduleScroll = intArrayOf(0, 0)
    private var scheduleViews = emptyList<ScrollView>()
    private var renderSchedule: (() -> Unit)? = null
    private var scheduleSignature = ""
    private val iconJobs = mutableListOf<Job>()
    private val countdownLabels = mutableListOf<Pair<TextView, SportsEvent>>()
    private val artworkCache = object : android.util.LruCache<Int, android.graphics.Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: android.graphics.Bitmap) = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sport = runCatching { Sport.valueOf(savedInstanceState?.getString("sport") ?: "FOOTBALL") }.getOrDefault(Sport.FOOTBALL)
        homeFocus = sport.name
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.PLAYER -> { stopPlayback(); showChannels() }
                    Screen.CHANNELS -> showSchedule()
                    Screen.SCHEDULE -> if (sport in Sport.more) showHome(true) else showHome()
                    Screen.MORE -> showHome()
                    Screen.HOME -> finish()
                }
            }
        })
        showHome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("sport", sport.name)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (screen == Screen.PLAYER && player == null && discoveryJob?.isActive != true) selectedChannel?.let { openChannel(it, streamIndex) }
        if (screen == Screen.SCHEDULE) {
            if (schedules[sport] == null && browseJob?.isActive != true) showSchedule() else startTicker()
        }
    }

    override fun onStop() {
        browseJob?.cancel(); tickerJob?.cancel(); stopPlayback()
        super.onStop()
    }

    private fun art(sport: Sport?): Int = when (sport) {
        Sport.FOOTBALL -> R.drawable.sport_football_cutout
        Sport.TENNIS -> R.drawable.sport_tennis_cutout
        Sport.RUGBY -> R.drawable.sport_rugby_cutout
        Sport.F1 -> R.drawable.sport_f1_cutout
        Sport.NFL -> R.drawable.sport_nfl_cutout
        Sport.NBA -> R.drawable.sport_nba_cutout
        Sport.MLB -> R.drawable.sport_mlb_cutout
        Sport.NHL -> R.drawable.sport_nhl_cutout
        Sport.GOLF -> R.drawable.sport_golf_cutout
        else -> R.drawable.sport_more_cutout
    }

    private fun artwork(item: Sport?) = ImageView(this).apply {
        val resource = art(item)
        val bitmap = artworkCache.get(resource) ?: android.graphics.BitmapFactory.decodeResource(resources, resource,
            android.graphics.BitmapFactory.Options()).also { artworkCache.put(resource, it) }
        setImageBitmap(bitmap); scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun shell(crumb: String, back: (() -> Unit)? = null): LinearLayout {
        browseJob?.cancel(); tickerJob?.cancel(); renderSchedule = null
        iconJobs.forEach { it.cancel() }; iconJobs.clear()
        val root = column().apply { background = browserBackground() }
        val header = row().apply { setPadding(dp(36), 0, dp(36), 0) }
        if (back != null) header.addView(label("‹", 24f, MUTED).apply {
            isFocusable = true; isClickable = true; gravity = Gravity.CENTER; contentDescription = "Back"
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(INK, ACCENT))
                addState(intArrayOf(), shape(Color.TRANSPARENT))
            }
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(32), dp(36)).apply { marginEnd = dp(12) })
        header.addView(label("SPORTS", 17f).apply { bold(); letterSpacing = .16f })
        header.addView(label(crumb, 12f, MUTED).apply { setPadding(dp(22), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label(SimpleDateFormat("EEE, MMM d  ·  h:mm a", Locale.getDefault()).format(Date()), 12f, MUTED))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
        val body = column().apply { setPadding(dp(36), dp(8), dp(36), dp(24)) }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        return body
    }

    private fun showHome(more: Boolean = false) {
        screen = if (more) Screen.MORE else Screen.HOME
        val body = shell(if (more) " /  More sports" else " /  Home")
        body.addSpaced(label(if (more) "More sports" else "Pick your sport.", 30f).apply { bold() }, bottom = 7)
        body.addSpaced(label("Find what’s on. Choose your channel. Settle in.", 13f, MUTED), bottom = 20)
        val list = column()
        val items: List<Sport?> = if (more) Sport.more else Sport.featured + listOf(null)
        val columns = if (more) 4 else 5
        var target: View? = null
        items.chunked(columns).forEach { group ->
            val line = row()
            group.forEach { item ->
                val key = item?.name ?: "MORE"
                val tile = FrameLayout(this).apply {
                    id = View.generateViewId(); tag = key
                    isFocusable = true; isClickable = true
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), shape(Color.TRANSPARENT, ACCENT))
                        addState(intArrayOf(android.R.attr.state_pressed), shape(Color.TRANSPARENT, ACCENT))
                        addState(intArrayOf(), shape(Color.TRANSPARENT).apply { setStroke(dp(1), Color.rgb(54, 64, 76)) })
                    }
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    addView(artwork(item), FrameLayout.LayoutParams(dp(132), dp(96), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                        .apply { topMargin = dp(12) })
                    addView(label(item?.label ?: "+ More", 18f).apply { bold(); gravity = Gravity.CENTER },
                        FrameLayout.LayoutParams(-1, dp(30), Gravity.BOTTOM).apply { bottomMargin = dp(8) })
                    contentDescription = item?.label ?: "More sports"
                    setOnClickListener {
                        if (!more) homeFocus = key
                        if (item == null) showHome(true)
                        else { sport = item; focusedEventId = null; scheduleScroll = intArrayOf(0, 0); showSchedule() }
                    }
                }
                if (key == (if (more) sport.name else homeFocus)) target = tile
                line.addView(tile, LinearLayout.LayoutParams(0, dp(155), 1f).apply { marginEnd = dp(12) })
            }
            repeat(columns - group.size) { line.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginEnd = dp(12) }) }
            list.addSpaced(line, bottom = 14)
        }
        body.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        if (more) body.addSpaced(action("‹  All sports") { showHome() }, dp(44), 0)
        (target ?: (list.getChildAt(0) as? LinearLayout)?.getChildAt(0))?.requestFocus()
    }

    private fun showSchedule(refresh: Boolean = false) {
        screen = Screen.SCHEDULE
        val body = shell(" /  ${sport.label}") { if (sport in Sport.more) showHome(true) else showHome() }
        val heading = row()
        heading.addView(artwork(sport),
            LinearLayout.LayoutParams(dp(132), dp(96)).apply { marginEnd = dp(22) })
        val title = column()
        title.addSpaced(label(sport.label, 30f).apply { bold() }, bottom = 6)
        title.addView(label("Schedule", 18f, MUTED))
        heading.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val refreshButton = action("Refresh") { rememberSchedule(); showSchedule(true) }
        heading.addView(refreshButton, LinearLayout.LayoutParams(dp(104), dp(42)))
        body.addSpaced(heading, dp(96), 12)
        val status = label("Loading schedule…", 11f, MUTED)
        body.addSpaced(status, bottom = 6)
        val list = column()
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        scheduleViews = listOf(scroll)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        var firstRender = true
        fun render() {
            val entries = schedules[sport].orEmpty()
            val now = System.currentTimeMillis()
            val all = (if (sport == Sport.TENNIS) listOf(SportsRepository.tennisChannel) + entries else entries)
                .distinctBy { it.id }.filter { Schedule.section(it, sport, now) != ScheduleSection.EARLIER }
            if (!firstRender) rememberSchedule()
            val restoreFocus = if (firstRender) focusedEventId else currentFocus?.tag as? String
            val focusedControl = currentFocus
            list.removeAllViews(); countdownLabels.clear()
            iconJobs.forEach { it.cancel() }; iconJobs.clear()
            var focusTarget: View? = null
            var firstCard: View? = null
            fun section(kind: ScheduleSection, always: Boolean = false) {
                val events = all.filter { Schedule.section(it, sport, now) == kind }.sortedBy { it.startsAt ?: Long.MAX_VALUE }
                if (events.isEmpty() && !always) return
                val color = if (kind == ScheduleSection.CURRENT) Color.rgb(105, 217, 174) else ACCENT
                val sectionHeader = row().apply { setPadding(0, dp(8), 0, 0) }
                sectionHeader.addView(label("${kind.label}   ${events.size}", 20f, color).apply { bold() })
                if (kind == ScheduleSection.CURRENT) sectionHeader.addView(
                    label("Based on scheduled times · live status may vary", 10f, MUTED).apply { setPadding(dp(18), 0, 0, 0) })
                list.addSpaced(sectionHeader, bottom = 10)
                if (events.isEmpty()) list.addSpaced(label("No ${kind.label.lowercase()} events listed", 13f, MUTED)
                    .apply { setPadding(dp(12), dp(10), 0, dp(12)) })
                events.forEach { event ->
                    val card = eventCard(event)
                    if (firstCard == null) firstCard = card
                    if (event.id == restoreFocus) focusTarget = card
                    list.addSpaced(card, dp(76), 8)
                }
            }
            section(ScheduleSection.CURRENT, true)
            section(ScheduleSection.UPCOMING, true)
            section(ScheduleSection.CHANNELS)
            section(ScheduleSection.UNKNOWN)
            val initial = firstRender
            scroll.post {
                scroll.scrollTo(0, scheduleScroll[0])
                val target = focusTarget ?: if (initial || focusedControl?.isAttachedToWindow == false) firstCard else null
                target?.requestFocus() ?: if (focusedControl?.isAttachedToWindow == true) focusedControl.requestFocus() else Unit
            }
            status.text = "${all.count { !it.isChannel }} events · local start times"
            scheduleSignature = signature()
            firstRender = false
        }
        renderSchedule = ::render
        if (!refresh && schedules[sport] != null) render()
        else {
            status.text = "Getting the latest events…"
            status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            list.addView(ScheduleLoadingView(this), LinearLayout.LayoutParams(-1, -2))
            val requested = sport
            browseJob = lifecycleScope.launch {
                try {
                    schedules[requested] = withContext(Dispatchers.IO) { repository.events(requested) }
                    if (screen == Screen.SCHEDULE && sport == requested) render()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    render()
                    status.text = if (schedules[requested] != null) "Couldn’t refresh · showing the previous schedule" else "Schedule unavailable · select Refresh to try again"
                }
            }
        }
        startTicker()
    }

    private fun signature(): String {
        val now = System.currentTimeMillis()
        return schedules[sport].orEmpty().joinToString { it.id + Schedule.section(it, sport, now).name }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (screen == Screen.SCHEDULE) {
                if (browseJob?.isActive != true && signature() != scheduleSignature) renderSchedule?.invoke()
                countdownLabels.forEach { (view, event) -> view.text = timing(event) }
                delay(15_000)
            }
        }
    }

    private fun rememberSchedule() { scheduleViews.forEachIndexed { index, view -> scheduleScroll[index] = view.scrollY } }

    private fun eventCard(event: SportsEvent): View = row().apply {
        tag = event.id; isFocusable = true; isClickable = true; background = focusBackground()
        setPadding(dp(5), dp(5), dp(16), dp(5))
        event.leagueIconUrl?.let { url ->
            val icon = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            addView(icon, LinearLayout.LayoutParams(dp(72), -1).apply { marginEnd = dp(12) })
            iconJobs += lifecycleScope.launch {
                val bitmap = LeagueIcons.load(url)
                if (bitmap != null) icon.setImageBitmap(bitmap)
            }
        } ?: setPadding(dp(18), dp(5), dp(16), dp(5))
        val text = column()
        text.addSpaced(label(event.competition.ifBlank { sport.label }, 12f, MUTED).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, bottom = 6)
        text.addView(label(event.title, 18f).apply { bold(); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(14) })
        val time = label(timing(event), 12f, if (event.startsAt?.let { it > System.currentTimeMillis() } == true) ACCENT else MUTED).apply {
            gravity = Gravity.CENTER_VERTICAL; maxLines = 2
        }
        countdownLabels += time to event
        addView(time, LinearLayout.LayoutParams(dp(135), -2).apply { marginEnd = dp(14) })
        addView(View(this@MainActivity).apply { setBackgroundColor(0xFF22374C.toInt()) }, LinearLayout.LayoutParams(dp(1), dp(34)).apply { marginEnd = dp(16) })
        addView(label("${event.channels.size} ${if (event.channels.size == 1) "channel" else "channels"}  ›", 12f, MUTED), LinearLayout.LayoutParams(dp(90), -2))
        contentDescription = "${event.title}, ${timing(event)}, ${event.channels.size} channels"
        setOnClickListener { rememberSchedule(); focusedEventId = event.id; selectedEvent = event; selectedChannel = null; showChannels() }
    }

    private fun eventTime(event: SportsEvent): String = event.startsAt?.let {
        SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(it))
    } ?: event.timeLabel.ifBlank { if (event.isChannel) "24/7 channel" else "Time unconfirmed" }

    private fun timing(event: SportsEvent): String {
        val now = System.currentTimeMillis()
        return event.startsAt?.let {
            if (it > now) "${Schedule.countdown(it, now)}\n${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))}"
            else "Started\n${SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(it))}"
        } ?: eventTime(event)
    }

    private fun showChannels() {
        screen = Screen.CHANNELS
        val event = selectedEvent ?: return showSchedule()
        val body = shell(" /  ${sport.label}  /  Channels") { showSchedule() }
        val hero = row().apply { gravity = Gravity.TOP }
        val eventArtwork = artwork(sport)
        hero.addView(eventArtwork, LinearLayout.LayoutParams(dp(132), dp(96)).apply { marginEnd = dp(22) })
        event.leagueIconUrl?.let { url ->
            iconJobs += lifecycleScope.launch {
                LeagueIcons.load(url)?.let { eventArtwork.setImageBitmap(it) }
            }
        }
        val text = column().apply { minimumHeight = dp(96); gravity = Gravity.CENTER_VERTICAL }
        text.addSpaced(label(event.competition.uppercase(), 11f, ACCENT), bottom = 8)
        text.addSpaced(label(event.title, 28f).apply { bold(); maxLines = 2 }, bottom = 10)
        text.addView(label(eventTime(event), 13f, MUTED))
        hero.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        body.addSpaced(hero, bottom = 20)
        body.addSpaced(label("Choose a channel", 20f).apply { bold() }, bottom = 16)
        val channels = column()
        var target: View? = null
        event.channels.forEach { channel ->
            val button = action("▶    ${channel.name}") { openChannel(channel) }
            button.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            channels.addSpaced(button, dp(52), 10)
            if (target == null || channel.id == selectedChannel?.id) target = button
        }
        body.addView(ScrollView(this).apply { addView(channels) }, LinearLayout.LayoutParams(-1, 0, 1f))
        target?.requestFocus()
    }

    private fun openChannel(channel: Channel, initialStream: Int = 0) {
        stopPlayback()
        screen = Screen.PLAYER; selectedChannel = channel; streamIndex = 0; streamOptions = emptyList(); retries = 0
        buildPlayer()
        // Start the channel immediately; discovering alternate players must not delay Stream 1.
        if (initialStream == 0) {
            streamOptions = listOf(channel.links.first().copy(label = "Stream 1"))
            resolveAndPlay()
        }
        discoveryJob = lifecycleScope.launch {
            try {
                streamOptions = withContext(Dispatchers.IO) { resolver.streams(channel) }
                ensureActive()
                streamIndex = initialStream.coerceIn(0, streamOptions.lastIndex)
                updateStreamControls()
                if (initialStream != 0) resolveAndPlay()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                streamOptions = channel.links.mapIndexed { index, link -> link.copy(label = "Stream ${index + 1}") }
                updateStreamControls()
                if (initialStream != 0) resolveAndPlay()
            }
        }
    }

    private fun buildPlayer() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val frame = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean = handlePlayerKey(event) || super.dispatchKeyEvent(event)
        }.apply { setBackgroundColor(Color.BLACK) }
        val video = PlayerView(this).apply {
            useController = false; isFocusable = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        }
        playerView = video
        frame.addView(video, FrameLayout.LayoutParams(-1, -1))
        val chrome = PlayerChrome(this, selectedEvent?.title ?: "Sports", selectedChannel?.name.orEmpty(),
            onBack = { stopPlayback(); showChannels() },
            onPrevious = { changeStream(-1) }, onNext = { changeStream(1) },
            onRetry = {
                if (streamOptions.isEmpty()) selectedChannel?.let { openChannel(it) }
                else { retries = 0; resolveAndPlay() }
            },
            onTogglePlay = { player?.let { if (it.playWhenReady) it.pause() else it.play() } })
        playerChrome = chrome
        frame.addView(chrome, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        updateStreamControls()
        chrome.requestFocus()
    }

    private fun updateStreamControls() {
        playerChrome?.setStreams(streamIndex, streamOptions.size)
    }

    private fun changeStream(direction: Int) {
        val target = streamIndex + direction
        if (target !in streamOptions.indices) return
        streamIndex = target
        retries = 0
        updateStreamControls()
        resolveAndPlay()
    }

    private fun handlePlayerKey(event: KeyEvent): Boolean {
        if (screen != Screen.PLAYER) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) { player?.play(); return true }
            if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) { player?.pause(); return true }
        }
        return playerChrome?.handleKey(event) == true
    }

    private fun resolveAndPlay() {
        val link = streamOptions.getOrNull(streamIndex) ?: return
        resolveJob?.cancel(); renewalJob?.cancel(); recoveryJob?.cancel(); stableJob?.cancel()
        player?.stop()
        updateStreamControls()
        playerChrome?.showConnecting()
        resolveJob = lifecycleScope.launch {
            try {
                val stream = withContext(Dispatchers.IO) { resolver.resolve(link) }
                ensureActive()
                if (screen != Screen.PLAYER) return@launch
                val httpFactory = DefaultHttpDataSource.Factory().setUserAgent(PageClient.USER_AGENT)
                    .setDefaultRequestProperties(stream.headers).setConnectTimeoutMs(12_000).setReadTimeoutMs(15_000)
                val media = HlsMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                val activePlayer = player ?: ExoPlayer.Builder(this@MainActivity).build().also {
                    player = it; playerView?.player = it
                    it.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            playerChrome?.showPlayback(it.isPlaying)
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> playerChrome?.showConnecting()
                                Player.STATE_READY -> playerChrome?.showPlayback(it.isPlaying)
                                Player.STATE_ENDED -> playerChrome?.showUnavailable()
                            }
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (it.playbackState == Player.STATE_READY) playerChrome?.showPlayback(isPlaying)
                            stableJob?.cancel()
                            if (isPlaying) stableJob = lifecycleScope.launch { delay(30_000); retries = 0 }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.w("SportsPlayer", error.errorCodeName); recover()
                        }
                    })
                }
                activePlayer.setMediaSource(media); activePlayer.prepare(); activePlayer.playWhenReady = true
                stream.expiresAtMillis?.let { expires ->
                    renewalJob = lifecycleScope.launch {
                        delay((expires - System.currentTimeMillis() - 60_000).coerceIn(15_000, 21_600_000))
                        resolveAndPlay()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { recover() }
        }
    }

    private fun recover() {
        if (screen != Screen.PLAYER || recoveryJob?.isActive == true) return
        renewalJob?.cancel(); retries++
        if (retries > 3) {
            playerChrome?.showUnavailable()
            return
        }
        playerChrome?.showConnecting()
        recoveryJob = lifecycleScope.launch { delay(retries * 3000L); resolveAndPlay() }
    }

    private fun stopPlayback() {
        discoveryJob?.cancel(); resolveJob?.cancel(); renewalJob?.cancel(); recoveryJob?.cancel(); stableJob?.cancel()
        playerView?.player = null; player?.release(); player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
