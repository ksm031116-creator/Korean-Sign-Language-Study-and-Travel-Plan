package app.signkorean.adapter.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.signkorean.domain.entitlement.NetworkMonitor

/** 인터넷이 실제로 되는 연결(VALIDATED)이 있는지. 공항 와이파이 로그인 전 같은 연결은 오프라인으로 본다. */
class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
