package io.github.zalexanninev15.tokitoki.data.telegram

/**
 * Loads the TDLib native library, and nothing else yet.
 *
 * Deliberately free of any TdApi reference. Those classes are generated while TDLib is
 * built, so until the `build tdlib` workflow has produced them there is nothing to
 * compile against — and writing hundreds of class names from memory would produce a
 * module that fails in ways no one can debug.
 *
 * What this does give: a module that exists, compiles, and packages, so the generated
 * sources have somewhere to land and the build is verified before any client code is
 * written on top of it.
 */
object TdlibNative {

    /** Result of trying to load the library, so the UI can explain a missing build. */
    sealed interface State {
        data object NotLoaded : State
        data object Ready : State
        data class Missing(val reason: String) : State
    }

    @Volatile
    private var state: State = State.NotLoaded

    @Synchronized
    fun load(): State {
        if (state is State.Ready) return state
        state = try {
            System.loadLibrary(LIBRARY)
            State.Ready
        } catch (e: UnsatisfiedLinkError) {
            // Expected until the workflow output is placed into jniLibs.
            State.Missing(e.message ?: "libtdjson not found for this ABI")
        }
        return state
    }

    fun current(): State = state

    val isAvailable: Boolean get() = load() is State.Ready

    private const val LIBRARY = "tdjson"
}
