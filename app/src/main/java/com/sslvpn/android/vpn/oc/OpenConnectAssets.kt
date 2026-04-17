package com.sslvpn.android.vpn.oc

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Copies curl + CSD helper from packaged assets into app-private storage (same pattern as OpenConnect for Android).
 */
object OpenConnectAssets {
    private const val CURL_ASSET = "raw/%s/curl-bin"
    private const val CSD_ASSET = "raw/noarch/android_csd.sh"

    fun prepareRuntimeFiles(context: Context): PreparedPaths? {
        val abi = preferredAssetAbi() ?: return null
        val curlAsset = CURL_ASSET.format(abi)
        val curlIn = openAsset(context, curlAsset) ?: return null
        val filesDir = context.filesDir
        val curlBin = File(filesDir, "curl-bin")
        copyStreamToFile(curlIn, curlBin)
        curlBin.setExecutable(true, false)

        val curlWrapper = File(filesDir, "curl")
        curlWrapper.writeText(
            "#!/system/bin/sh\nexec '${curlBin.absolutePath.replace("'", "'\\''")}' \"\$@\"\n"
        )
        curlWrapper.setExecutable(true, false)

        val csdIn = openAsset(context, CSD_ASSET) ?: return null
        val csdFile = File(filesDir, "android_csd.sh")
        copyStreamToFile(csdIn, csdFile)
        csdFile.setExecutable(true, false)

        val path = "${filesDir.absolutePath}:${System.getenv("PATH") ?: ""}"
        return PreparedPaths(
            csdScript = csdFile.absolutePath,
            cacheDir = context.cacheDir.absolutePath,
            path = path
        )
    }

    private fun preferredAssetAbi(): String? {
        for (abi in Build.SUPPORTED_ABIS) {
            when (abi) {
                "arm64-v8a", "x86_64", "x86" -> return abi
                "armeabi-v7a" -> return "armeabi"
            }
        }
        return null
    }

    private fun openAsset(context: Context, path: String): InputStream? =
        runCatching { context.assets.open(path) }.getOrNull()

    private fun copyStreamToFile(input: InputStream, out: File) {
        FileOutputStream(out).use { fos -> input.use { it.copyTo(fos) } }
    }

    data class PreparedPaths(
        val csdScript: String,
        val cacheDir: String,
        val path: String
    )
}
