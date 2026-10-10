/*
 * Centium Android VPN - Native JNI Bridge for hev-socks5-tunnel
 * Routes Android VpnService TUN file descriptor into the lwIP user-space SOCKS5 engine.
 */

#include <jni.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>

// Function signatures provided by hev-socks5-tunnel core C library
extern int hev_socks5_tunnel_main_from_file (const char *config_path, int tun_fd);
extern void hev_socks5_tunnel_quit (void);
extern void hev_socks5_tunnel_stats (size_t *tx_packets, size_t *tx_bytes,
                                     size_t *rx_packets, size_t *rx_bytes);

static pthread_t work_thread;
static volatile int is_running = 0;
static char current_config[1024];
static int current_fd = -1;

static void *
thread_worker (void *data)
{
    is_running = 1;
    hev_socks5_tunnel_main_from_file (current_config, current_fd);
    is_running = 0;
    return NULL;
}

JNIEXPORT jboolean JNICALL
Java_hev_htproxy_TProxyService_TProxyStartService (JNIEnv *env, jclass clazz,
                                                   jstring config_path, jint fd)
{
    if (is_running)
        return JNI_TRUE;

    const char *str = (*env)->GetStringUTFChars (env, config_path, NULL);
    if (!str)
        return JNI_FALSE;

    strncpy (current_config, str, sizeof (current_config) - 1);
    current_config[sizeof (current_config) - 1] = '\0';
    (*env)->ReleaseStringUTFChars (env, config_path, str);

    current_fd = fd;

    if (pthread_create (&work_thread, NULL, thread_worker, NULL) != 0) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_hev_htproxy_TProxyService_TProxyStopService (JNIEnv *env, jclass clazz)
{
    if (!is_running)
        return JNI_TRUE;

    hev_socks5_tunnel_quit ();
    pthread_join (work_thread, NULL);
    is_running = 0;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_hev_htproxy_TProxyService_TProxyIsRunning (JNIEnv *env, jclass clazz)
{
    return is_running ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL
Java_hev_htproxy_TProxyService_TProxyGetStats (JNIEnv *env, jclass clazz)
{
    size_t tx_packets = 0, tx_bytes = 0;
    size_t rx_packets = 0, rx_bytes = 0;

    hev_socks5_tunnel_stats (&tx_packets, &tx_bytes, &rx_packets, &rx_bytes);

    jlongArray array = (*env)->NewLongArray (env, 4);
    if (!array)
        return NULL;

    jlong data[4] = {
        (jlong) tx_packets,
        (jlong) tx_bytes,
        (jlong) rx_packets,
        (jlong) rx_bytes
    };

    (*env)->SetLongArrayRegion (env, array, 0, 4, data);
    return array;
}
