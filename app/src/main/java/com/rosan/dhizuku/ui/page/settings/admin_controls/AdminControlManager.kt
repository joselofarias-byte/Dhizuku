package com.rosan.dhizuku.ui.page.settings.admin_controls

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.rosan.dhizuku.server.DhizukuState

data class AdminAdbState(
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val adbEnabled: Boolean,
    val debuggingRestricted: Boolean
)

data class ManagedAppPolicy(
    val label: String,
    val packageName: String,
    val hidden: Boolean,
    val uninstallBlocked: Boolean
)

class AdminControlManager(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val pm = context.packageManager
    private val admin get() = DhizukuState.admin

    fun readAdbState(): AdminAdbState {
        val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
        val isProfileOwner = dpm.isProfileOwnerApp(context.packageName)
        val adbEnabled = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
        val debuggingRestricted = userManager.hasUserRestriction(
            UserManager.DISALLOW_DEBUGGING_FEATURES
        )
        return AdminAdbState(
            isDeviceOwner = isDeviceOwner,
            isProfileOwner = isProfileOwner,
            adbEnabled = adbEnabled,
            debuggingRestricted = debuggingRestricted
        )
    }

    fun setAdbEnabled(enabled: Boolean): Result<AdminAdbState> = runCatching {
        check(dpm.isDeviceOwnerApp(context.packageName)) {
            "ADB sólo puede controlarse cuando Dhizuku es Propietario del dispositivo."
        }

        // Si nuestra propia política bloqueaba depuración, retirarla antes de encender ADB.
        if (enabled) {
            runCatching {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
        }

        dpm.setGlobalSetting(
            admin,
            Settings.Global.ADB_ENABLED,
            if (enabled) "1" else "0"
        )

        val state = readAdbState()
        check(state.adbEnabled == enabled) {
            "Android aceptó la política pero ADB no cambió al estado solicitado."
        }
        state
    }

    fun listUserApps(): List<ManagedAppPolicy> {
        check(DhizukuState.state.isOwner) {
            "Dhizuku no es propietario del dispositivo o perfil."
        }

        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }

        return apps
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { info ->
                val packageName = info.packageName
                ManagedAppPolicy(
                    label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(packageName),
                    packageName = packageName,
                    hidden = runCatching {
                        dpm.isApplicationHidden(admin, packageName)
                    }.getOrDefault(false),
                    uninstallBlocked = runCatching {
                        dpm.isUninstallBlocked(admin, packageName)
                    }.getOrDefault(false)
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    fun setApplicationHidden(packageName: String, hidden: Boolean): Result<Boolean> = runCatching {
        require(packageName != context.packageName) {
            "Dhizuku no puede ocultarse a sí mismo desde este panel."
        }
        check(DhizukuState.state.isOwner) {
            "Dhizuku no tiene privilegios de propietario."
        }
        val changed = dpm.setApplicationHidden(admin, packageName, hidden)
        check(changed) {
            "Android rechazó el cambio de visibilidad para $packageName."
        }
        dpm.isApplicationHidden(admin, packageName)
    }

    fun setUninstallBlocked(packageName: String, blocked: Boolean): Result<Boolean> = runCatching {
        require(packageName != context.packageName) {
            "Dhizuku no puede administrarse a sí mismo desde este panel."
        }
        check(DhizukuState.state.isOwner) {
            "Dhizuku no tiene privilegios de propietario."
        }
        dpm.setUninstallBlocked(admin, packageName, blocked)
        val actual = dpm.isUninstallBlocked(admin, packageName)
        check(actual == blocked) {
            "Android no aplicó el bloqueo de desinstalación solicitado."
        }
        actual
    }
}
