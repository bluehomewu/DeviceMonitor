package tw.bluehomewu.devicemonitor

object AppConfig {
    /**
     * Force all users to re-signin if their last auth predates this version.
     * Set to a version string (e.g. "1.14.0") to trigger re-signin on next app launch.
     * Set to null to disable.
     */
    @Suppress("RedundantNullableReturnType")
    val FORCE_RESIGN_FROM_VERSION: String? = "1.13.0"
}
