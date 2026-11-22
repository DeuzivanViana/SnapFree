package com.snapfree.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfree.app.AppContainer
import com.snapfree.app.engine.DownloadSpec
import com.snapfree.app.engine.DownloadType
import com.snapfree.app.engine.EngineState
import com.snapfree.app.engine.FormatCatalog
import com.snapfree.app.engine.MediaInfo
import com.snapfree.app.engine.YtDlpEngine
import com.snapfree.app.data.DefaultMode
import com.snapfree.app.data.LoginSite
import com.snapfree.app.util.LinkCheck
import com.snapfree.app.util.LinkDetector
import com.snapfree.app.util.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OptionTab { VIDEO, AUDIO, FORMATS }

sealed interface HomePhase {
    data object Idle : HomePhase
    data object Loading : HomePhase
    data class Error(val message: String) : HomePhase
    data class Loaded(val info: MediaInfo) : HomePhase
}

data class Selection(
    val tab: OptionTab = OptionTab.VIDEO,
    val container: String = "mp4",
    /** 0 = best available. */
    val height: Int = 0,
    val audioFormat: String = "mp3",
    val audioBitrate: Int = 192,
    val customFormatId: String? = null,
)

data class HomeUiState(
    val url: String = "",
    /** Live result of [LinkDetector] for [url], used for the status line under the field. */
    val link: LinkCheck = LinkCheck.Empty,
    val phase: HomePhase = HomePhase.Idle,
    val selection: Selection = Selection(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val engineState: StateFlow<EngineState> = container.engine.state
    val updating: StateFlow<Boolean> = container.engine.updating
    val loggedIn: StateFlow<Set<LoginSite>> = container.cookies.loggedIn

    private val _events = MutableStateFlow<HomeEvent?>(null)
    val events: StateFlow<HomeEvent?> = _events.asStateFlow()

    /** Waits for the user to stop typing before analyzing. */
    private var debounceJob: Job? = null
    private var fetchJob: Job? = null

    /** [LinkCheck.Complete.key] of the result shown or being fetched. The same media is never fetched twice in a row. */
    private var currentKey: String? = null

    /** Recent successful analyses, so going back to a link shows it instantly without a new request. */
    private val cache = object : LinkedHashMap<String, Pair<Long, MediaInfo>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, MediaInfo>>) = size > CACHE_SIZE
    }

    /**
     * Called on every keystroke or paste. Analysis starts by itself once the text is a complete link:
     * almost immediately after a paste, after a short pause while typing.
     */
    fun onUrlChange(value: String) {
        val previous = _state.value.url
        val check = LinkDetector.check(value)
        _state.update { it.copy(url = value, link = check) }
        debounceJob?.cancel()

        when (check) {
            LinkCheck.Empty -> resetResult()
            is LinkCheck.Incomplete -> {
                // Keep a loaded result on screen while the user edits, but drop work for a link that no longer exists.
                if (_state.value.phase !is HomePhase.Loaded) resetResult()
            }
            is LinkCheck.Complete -> {
                if (check.key == currentKey) return // Same media (maybe with other tracking params): nothing to do.
                // A different link replaces the one being fetched right away, so its result never shows up.
                if (_state.value.phase is HomePhase.Loading) fetchJob?.cancel()
                val pasted = value.length - previous.length >= PASTE_MIN_CHARS
                val wait = when {
                    pasted -> PASTE_DEBOUNCE_MS
                    check.confident -> TYPING_DEBOUNCE_MS
                    else -> TYPING_GENERIC_DEBOUNCE_MS
                }
                debounceJob = viewModelScope.launch {
                    delay(wait)
                    start(check, rewriteField = pasted, force = false)
                }
            }
        }
    }

    /** Paste button: fills the field and analyzes at once when the clipboard holds a complete link. */
    fun onPaste(text: String) {
        val check = LinkDetector.check(text)
        if (check is LinkCheck.Complete) analyze(text) else onUrlChange(text.trim())
    }

    fun clear() {
        debounceJob?.cancel()
        fetchJob?.cancel()
        currentKey = null
        _state.update { HomeUiState() }
    }

    /**
     * Explicit analysis (keyboard search action, shared link, retry, after login). Accepts any URL-like
     * text, even if it does not match a known pattern. [force] skips the cache and the duplicate check.
     */
    fun analyze(url: String = _state.value.url, force: Boolean = false) {
        debounceJob?.cancel()
        val check = LinkDetector.check(url, lenient = true)
        if (check !is LinkCheck.Complete) {
            _state.update { it.copy(url = url, link = check) }
            return
        }
        _state.update { it.copy(url = url, link = check) }
        val phase = _state.value.phase
        if (!force && check.key == currentKey && (phase is HomePhase.Loading || phase is HomePhase.Loaded)) return
        start(check, rewriteField = true, force = force)
    }

    private fun start(check: LinkCheck.Complete, rewriteField: Boolean, force: Boolean) {
        // Drop share-tracking parameters such as YouTube si= or Instagram igsh=.
        val url = UrlUtils.clean(check.url)
        fetchJob?.cancel()
        currentKey = check.key
        if (rewriteField) _state.update { it.copy(url = url) }

        val cached = cache[check.key]?.takeIf { !force && System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.second
        if (cached == null) _state.update { it.copy(phase = HomePhase.Loading) }
        fetchJob = viewModelScope.launch {
            try {
                val info = cached ?: container.engine.fetchInfo(url).also {
                    cache[check.key] = System.currentTimeMillis() to it
                }
                val selection = defaultSelection(info)
                _state.update { it.copy(phase = HomePhase.Loaded(info), selection = selection) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(phase = HomePhase.Error(YtDlpEngine.cleanError(t))) }
            }
        }
    }

    private fun resetResult() {
        fetchJob?.cancel()
        currentKey = null
        _state.update { it.copy(phase = HomePhase.Idle) }
    }

    fun select(transform: (Selection) -> Selection) = _state.update { it.copy(selection = transform(it.selection)) }

    fun download(formatLabel: String) {
        val loaded = _state.value.phase as? HomePhase.Loaded ?: return
        val s = _state.value.selection
        val spec = when (s.tab) {
            OptionTab.VIDEO -> DownloadSpec(type = DownloadType.VIDEO, container = s.container, maxHeight = s.height)
            OptionTab.AUDIO -> DownloadSpec(type = DownloadType.AUDIO, audioFormat = s.audioFormat, audioBitrate = s.audioBitrate)
            OptionTab.FORMATS -> {
                val format = loaded.info.formats.firstOrNull { it.formatId == s.customFormatId } ?: return
                DownloadSpec(type = DownloadType.CUSTOM, formatId = format.formatId, mergeAudio = format.isVideoOnly)
            }
        }
        viewModelScope.launch {
            container.downloads.enqueue(loaded.info, spec, formatLabel)
            _events.value = HomeEvent.Enqueued(System.currentTimeMillis())
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    private suspend fun defaultSelection(info: MediaInfo): Selection {
        val settings = container.settings.current()
        val heights = info.videoQualities.map { it.height }
        val height = when {
            settings.maxHeight == 0 -> 0
            heights.isEmpty() -> 0
            settings.maxHeight in heights -> settings.maxHeight
            else -> heights.filter { it <= settings.maxHeight }.maxOrNull() ?: 0
        }
        val tab = if (settings.defaultMode == DefaultMode.AUDIO || !info.hasVideo) OptionTab.AUDIO else OptionTab.VIDEO
        return Selection(
            tab = tab,
            container = settings.videoContainer.takeIf { it in FormatCatalog.videoContainers } ?: "mp4",
            height = height,
            audioFormat = settings.audioFormat.takeIf { it in FormatCatalog.audioFormats } ?: "mp3",
            audioBitrate = settings.audioBitrate,
            customFormatId = info.formats.firstOrNull()?.formatId,
        )
    }
}

private const val CACHE_SIZE = 24
private const val CACHE_TTL_MS = 20 * 60 * 1000L

/** A jump of this many characters in one change is treated as a paste. */
private const val PASTE_MIN_CHARS = 6
private const val PASTE_DEBOUNCE_MS = 150L
private const val TYPING_DEBOUNCE_MS = 700L
private const val TYPING_GENERIC_DEBOUNCE_MS = 1400L

sealed interface HomeEvent {
    data class Enqueued(val at: Long) : HomeEvent
}
