package org.centium.vpn.update

/** x.y.z release version. [code] matches the versionCode formula in app/build.gradle.kts. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {

    val code: Int get() = major * 10000 + minor * 100 + patch

    override fun compareTo(other: Version): Int = code.compareTo(other.code)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d{1,2})\.(\d{1,2})\.(\d{1,2})""")

        fun parse(text: String): Version? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (a, b, c) = m.destructured
            return Version(a.toInt(), b.toInt(), c.toInt())
        }
    }
}
