package com.sslvpn.android.vpn.oc

import android.content.res.Resources
import com.sslvpn.android.R

/**
 * Maps libopenconnect negative return codes (typically negated errno on Unix) to readable text.
 * Note: (-5) is usually -EIO — TLS/HTTP I/O or malformed response, not exclusively "wrong password".
 */
internal object OpenConnectErrorMapper {
    fun obtainCookieFailure(resources: Resources, code: Int): String {
        val hint = when (code) {
            -5 -> resources.getString(R.string.openconnect_err_eio)
            -1 -> resources.getString(R.string.openconnect_err_eperm)
            -2 -> resources.getString(R.string.openconnect_err_enoent)
            -22 -> resources.getString(R.string.openconnect_err_einval)
            -110 -> resources.getString(R.string.openconnect_err_etimedout)
            -113 -> resources.getString(R.string.openconnect_err_ehostunreach)
            else -> resources.getString(R.string.openconnect_err_other)
        }
        return resources.getString(R.string.openconnect_err_auth_template, code, hint)
    }
}
