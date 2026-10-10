package hev.htproxy

/**
 * Native JNI interface matching upstream hev-socks5-tunnel.
 * Directly integrates with libhev-socks5-tunnel.so on Android.
 */
object TProxyService {

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
        } catch (e: UnsatisfiedLinkError) {
            // Log fallback indicator if running in simulation / non-native unit tests
            System.err.println("Note: libhev-socks5-tunnel.so not preloaded: ${e.message}")
        }
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    external fun TProxyStopService(): Boolean

    @JvmStatic
    external fun TProxyIsRunning(): Boolean

    @JvmStatic
    external fun TProxyGetStats(): LongArray
}
