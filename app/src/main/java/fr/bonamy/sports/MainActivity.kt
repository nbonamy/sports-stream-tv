package fr.bonamy.sports

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
    private enum class Screen { BROWSE, DETAILS, PLAYER }
    private var screen = Screen.BROWSE
    private var sport = Sport.SOCCER
    private val repository = SportsRepository()
    private val resolver = StreamResolver(trace = { android.util.Log.d("SportsSource", it) })
    private val schedules = mutableMapOf<Sport, List<SportsEvent>>()
    private var selectedEvent: SportsEvent? = null
    private var selectedLink: StreamLink? = null
    private var browseJob: Job? = null
    private var resolveJob: Job? = null
    private var renewalJob: Job? = null
    private var recoveryJob: Job? = null
    private var stableJob: Job? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playerMessage: TextView? = null
    private var playerOverlay: LinearLayout? = null
    private var playerHeader: LinearLayout? = null
    private var retries = 0
    private var query = ""
    private var listScroll = 0
    private var focusedEventId: String? = null
    private var browseScroll: ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sport = runCatching { Sport.valueOf(savedInstanceState?.getString("sport") ?: "SOCCER") }.getOrDefault(Sport.SOCCER)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    Screen.PLAYER -> { stopPlayback(); showDetails() }
                    Screen.DETAILS -> showBrowse()
                    Screen.BROWSE -> finish()
                }
            }
        })
        showBrowse()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("sport", sport.name)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (screen == Screen.PLAYER && player == null) selectedLink?.let { openPlayer(it) }
        else if (screen == Screen.BROWSE && schedules[sport] == null && browseJob?.isActive != true) showBrowse()
    }

    override fun onStop() {
        browseJob?.cancel()
        stopPlayback()
        super.onStop()
    }

    private fun switchSport(next: Sport) {
        sport = next; query = ""; listScroll = 0; focusedEventId = null
        showBrowse()
    }

    private fun showBrowse(refresh: Boolean = false) {
        screen = Screen.BROWSE
        browseJob?.cancel()
        val root = column().apply { background = browserBackground() }
        val header = row().apply {
            setBackgroundColor(0xF207111D.toInt()); setPadding(dp(36), 0, dp(36), 0)
        }
        header.addView(label("Sports  /  ${sport.label}", 15f, Color.rgb(168, 189, 216)), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("SOCCER  +  TENNIS", 10f, MUTED).apply { letterSpacing = .12f })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(56)))
        val content = column().apply { setPadding(dp(36), dp(16), dp(36), 0) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val tabs = row()
        val sportButtons = Sport.entries.map { item ->
            action(item.label) { switchSport(item) }.apply { if (sport == item) setTextColor(ACCENT) }
        }
        sportButtons.forEach { tabs.addView(it, LinearLayout.LayoutParams(dp(120), dp(44)).apply { marginEnd = dp(12) }) }
        tabs.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        tabs.addView(action("Refresh") { showBrowse(true) }, LinearLayout.LayoutParams(dp(104), dp(44)))
        content.addSpaced(tabs, bottom = 14)
        val search = EditText(this).apply {
            typeface = resources.getFont(R.font.theme)
            hint = "Find a match, team or competition"; textSize = 13f
            setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(MUTED)
            background = focusBackground(); setPadding(dp(14), dp(6), dp(14), dp(6)); setText(query)
        }
        content.addSpaced(search, dp(38), 10)
        val count = label("Loading schedule…", 11f, MUTED)
        content.addSpaced(count, bottom = 6)
        val list = column().apply { clipChildren = false; setPadding(0, 0, 0, dp(36)) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true; clipToPadding = false; clipChildren = false
            isVerticalScrollBarEnabled = false; addView(list)
        }
        browseScroll = scroll
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        fun render() {
            list.removeAllViews()
            val entries = schedules[sport].orEmpty()
            val all = if (sport == Sport.TENNIS) listOf(SportsRepository.tennisChannel) + entries else entries
            val visible = all.filter { query.isBlank() || "${it.title} ${it.competition}".contains(query, ignoreCase = true) }
            count.text = "${entries.size} listings · select a match to choose a source"
            val groups = visible.groupBy { if (it == SportsRepository.tennisChannel) "Channels" else it.competition.ifBlank { "Matches" } }
            groups.forEach { (title, events) ->
                list.addSpaced(label(title, 16f).apply { bold(); setPadding(0, dp(12), 0, 0) }, bottom = 10)
                val cards = row().apply { clipChildren = false; setPadding(dp(3), dp(3), dp(3), dp(3)) }
                events.forEach { event ->
                    val card = eventCard(event)
                    cards.addView(card, LinearLayout.LayoutParams(dp(224), dp(194)).apply { marginEnd = dp(14) })
                    if (event.id == focusedEventId) card.post { card.requestFocus() }
                }
                list.addSpaced(HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false; clipToPadding = false; clipChildren = false; addView(cards)
                }, bottom = 8)
            }
            if (visible.isEmpty()) list.addSpaced(label("No matches found. Try another search or refresh.", 16f, MUTED))
            scroll.post { scroll.scrollTo(0, listScroll) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s.toString(); focusedEventId = null; listScroll = 0; render()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        if (!refresh && schedules[sport] != null) render()
        else {
            list.addSpaced(label("Loading the ${sport.label.lowercase()} schedule…", 18f, MUTED))
            val requestedSport = sport
            browseJob = lifecycleScope.launch {
                try {
                    val loaded = withContext(Dispatchers.IO) { repository.events(requestedSport) }
                    schedules[requestedSport] = loaded
                    if (screen == Screen.BROWSE && sport == requestedSport) render()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    if (schedules[requestedSport] != null) {
                        render(); count.text = "Couldn’t refresh · showing the previous schedule"
                    } else {
                        list.removeAllViews()
                        list.addSpaced(label("The schedule isn’t available right now.", 20f))
                        list.addSpaced(label("Check your connection, then try again.", 14f, MUTED))
                        list.addSpaced(action("Try again") { showBrowse(true) })
                        if (sport == Sport.TENNIS) list.addSpaced(eventCard(SportsRepository.tennisChannel), dp(194))
                    }
                }
            }
        }
        sportButtons[sport.ordinal].requestFocus()
    }

    private fun eventCard(event: SportsEvent): View = column().apply {
        background = focusBackground(); isFocusable = true; isClickable = true
        setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(SportArt(this@MainActivity, sport, event.title), LinearLayout.LayoutParams(-1, dp(94)))
        val text = column().apply { setPadding(dp(10), dp(9), dp(10), dp(8)) }
        text.addSpaced(label(event.title, 14f).apply {
            bold(); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, dp(36), 6)
        text.addSpaced(label(eventTime(event), 10f, MUTED).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, bottom = 6)
        text.addView(label("${event.links.size} ${if (event.links.size == 1) "source" else "sources"}", 10f, ACCENT))
        addView(text)
        contentDescription = "${event.title}, ${eventTime(event)}, ${event.links.size} sources"
        setOnClickListener {
            listScroll = browseScroll?.scrollY ?: 0; focusedEventId = event.id
            selectedEvent = event; showDetails()
        }
    }

    private fun eventTime(event: SportsEvent): String = event.startsAt?.let {
        SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()).format(Date(it))
    } ?: event.timeLabel.ifBlank { "Channel" }

    private fun showDetails() {
        screen = Screen.DETAILS
        browseJob?.cancel()
        val event = selectedEvent ?: return showBrowse()
        val content = column().apply { background = browserBackground(); setPadding(dp(48), dp(28), dp(48), dp(28)) }
        content.addSpaced(action("‹  Back to ${sport.label}") { showBrowse() }, dp(46), 26)
        content.addSpaced(label(event.competition.uppercase(), 12f, ACCENT).apply { letterSpacing = .12f })
        content.addSpaced(label(event.title, 30f).apply { bold() })
        content.addSpaced(label(eventTime(event), 14f, MUTED), bottom = 26)
        content.addSpaced(label("Choose a source", 20f).apply { bold() }, bottom = 8)
        content.addSpaced(label("If a source is unavailable, try another. Broadcast commercials may still appear.", 12f, MUTED), bottom = 20)
        val options = column()
        event.links.forEachIndexed { index, link ->
            options.addSpaced(action("${index + 1}    ${link.label}    ▶") { openPlayer(link) }, dp(56))
        }
        content.addView(ScrollView(this).apply { addView(options) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
        options.getChildAt(0)?.requestFocus()
    }

    private fun openPlayer(link: StreamLink) {
        stopPlayback()
        screen = Screen.PLAYER; selectedLink = link; retries = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val video = PlayerView(this).apply {
            useController = true; controllerShowTimeoutMs = 5000
            controllerAutoShow = false; setShowNextButton(false); setShowPreviousButton(false)
            setShowFastForwardButton(false); setShowRewindButton(false)
        }
        playerView = video
        frame.addView(video, FrameLayout.LayoutParams(-1, -1))
        val header = row().apply { setPadding(dp(24), dp(14), dp(24), dp(14)); setBackgroundColor(0xCC0C1015.toInt()) }
        header.addView(action("‹  Sources") { stopPlayback(); showDetails() })
        header.addView(label(selectedEvent?.title ?: "Sports", 18f).apply { setPadding(dp(20), 0, dp(10), 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(action("Reconnect") { retries = 0; resolveAndPlay() })
        frame.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        playerHeader = header
        val overlay = column().apply { gravity = Gravity.CENTER; setPadding(dp(28), dp(24), dp(28), dp(24)); background = shape(0xEE171E26.toInt()) }
        val message = label("Finding your stream…", 20f).apply { gravity = Gravity.CENTER }
        playerMessage = message
        overlay.addSpaced(message, bottom = 16)
        overlay.addSpaced(action("Try again") { retries = 0; resolveAndPlay() })
        overlay.addView(action("Choose another source") { stopPlayback(); showDetails() })
        frame.addView(overlay, FrameLayout.LayoutParams(dp(480), -2, Gravity.CENTER))
        playerOverlay = overlay
        video.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            header.visibility = if (overlay.visibility == View.VISIBLE) View.VISIBLE else visibility
        })
        setContentView(frame)
        video.requestFocus()
        resolveAndPlay()
    }

    private fun resolveAndPlay() {
        val link = selectedLink ?: return
        resolveJob?.cancel(); renewalJob?.cancel(); recoveryJob?.cancel()
        playerMessage?.text = if (player == null) "Finding your stream…" else "Reconnecting…"
        if (player?.isPlaying != true) playerOverlay?.visibility = View.VISIBLE
        resolveJob = lifecycleScope.launch {
            try {
                val stream = withContext(Dispatchers.IO) { resolver.resolve(link) }
                ensureActive()
                if (screen != Screen.PLAYER) return@launch
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(PageClient.USER_AGENT).setDefaultRequestProperties(stream.headers)
                    .setConnectTimeoutMs(12_000).setReadTimeoutMs(15_000)
                val media = HlsMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                val activePlayer = player ?: ExoPlayer.Builder(this@MainActivity).build().also {
                    player = it; playerView?.player = it
                    it.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() {
                            playerOverlay?.visibility = View.GONE
                            playerHeader?.visibility = View.GONE
                            playerView?.requestFocus()
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            stableJob?.cancel()
                            if (isPlaying) stableJob = lifecycleScope.launch { delay(30_000); retries = 0 }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            android.util.Log.w("SportsPlayer", error.errorCodeName)
                            recover()
                        }
                    })
                }
                activePlayer.setMediaSource(media)
                activePlayer.prepare(); activePlayer.playWhenReady = true
                playerMessage?.text = "Starting the broadcast…"
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
        renewalJob?.cancel()
        retries++
        playerOverlay?.visibility = View.VISIBLE; playerHeader?.visibility = View.VISIBLE
        if (retries > 3) {
            playerMessage?.text = "This source isn’t available right now. Try again or choose another."
            playerOverlay?.getChildAt(1)?.requestFocus()
            return
        }
        playerMessage?.text = "Stream interrupted. Reconnecting ($retries/3)…"
        recoveryJob = lifecycleScope.launch { delay(retries * 3000L); resolveAndPlay() }
    }

    private fun stopPlayback() {
        resolveJob?.cancel(); renewalJob?.cancel(); recoveryJob?.cancel(); stableJob?.cancel()
        playerView?.player = null; player?.release(); player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
