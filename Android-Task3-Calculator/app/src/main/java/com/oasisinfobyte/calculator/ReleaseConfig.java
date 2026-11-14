package com.oasisinfobyte.calculator;

/** Public GitHub project used as the signed APK update channel. */
final class ReleaseConfig {
    static final String OWNER = "Vikas-daddi";
    static final String REPOSITORY = "OIBSIP";
    static final String APK_ASSET_NAME = "oasis-calculator.apk";
    static final String RELEASES_API = "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/latest";
    static final String RELEASE_DOWNLOAD_PREFIX = "https://github.com/" + OWNER + "/" + REPOSITORY + "/releases/download/";

    private ReleaseConfig() { }
}
