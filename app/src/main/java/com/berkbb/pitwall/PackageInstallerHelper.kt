package com.berkbb.pitwall

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import java.util.zip.ZipFile

enum class InstallStartResult { STARTED, FAILED, NO_COMPATIBLE_ABI }

object PackageInstallerHelper {

    private val KNOWN_ABIS = listOf("arm64_v8a", "armeabi_v7a", "x86_64", "x86", "armeabi")

    fun installApkm(context: Context, apkmFile: File): InstallStartResult {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(F1TV_PACKAGE_NAME)

        var session: PackageInstaller.Session? = null
        try {
            ZipFile(apkmFile).use { zip ->
                val apkEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && splitName(it.name).endsWith(".apk") }
                    .toList()

                // Cihazın desteklediği ABI'ler arasından pakette gerçekten bulunan en iyisini seç
                val bundleAbis = apkEntries.mapNotNull { extractAbi(splitName(it.name)) }.toSet()
                val bestAbi = if (bundleAbis.isEmpty()) null else {
                    resolveBestAbi(android.os.Build.SUPPORTED_ABIS, bundleAbis)
                        ?: return InstallStartResult.NO_COMPATIBLE_ABI
                }

                val sessionId = packageInstaller.createSession(params)
                session = packageInstaller.openSession(sessionId)
                val activeSession = session!!

                val written = mutableSetOf<String>()
                for (entry in apkEntries) {
                    val name = splitName(entry.name)
                    if (!shouldIncludeSplit(name, bestAbi) || !written.add(name)) continue

                    // base.apk, cihaza uygun mimari spliti, dil ve ekran yoğunluğu (dpi) splitlerini yükle
                    zip.getInputStream(entry).use { inStream ->
                        activeSession.openWrite(name, 0, entry.size).use { outStream ->
                            inStream.copyTo(outStream)
                            activeSession.fsync(outStream)
                        }
                    }
                }

                // Kurulumu başlat (Android TV yerel onay kutucuğunu açar)
                commit(context, activeSession, sessionId, selfUpdate = false)
            }
            return InstallStartResult.STARTED
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                session?.abandon()
            } catch (_: Exception) {}
            return InstallStartResult.FAILED
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {}
        }
    }

    /** Installs a newer Pitwall Updater APK over this app; the TV asks for confirmation. */
    fun installSelfApk(context: Context, apkFile: File): InstallStartResult {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apkFile.length())

        var session: PackageInstaller.Session? = null
        return try {
            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)
            apkFile.inputStream().use { inStream ->
                session.openWrite("base.apk", 0, apkFile.length()).use { outStream ->
                    inStream.copyTo(outStream)
                    session.fsync(outStream)
                }
            }
            commit(context, session, sessionId, selfUpdate = true)
            InstallStartResult.STARTED
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                session?.abandon()
            } catch (_: Exception) {}
            InstallStartResult.FAILED
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {}
        }
    }

    /** Commits a session; [InstallResultReceiver] gets the result, tagged with [selfUpdate]. */
    private fun commit(context: Context, session: PackageInstaller.Session, sessionId: Int, selfUpdate: Boolean) {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .putExtra(InstallResultReceiver.EXTRA_SELF_UPDATE, selfUpdate)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(pendingIntent.intentSender)
    }

    /** Session split names must not contain path separators; keep only the file name. */
    fun splitName(entryName: String): String = entryName.substringAfterLast('/')

    fun normalizeAbi(abi: String): String = abi.replace("-", "_")

    /** First device ABI (in preference order) that the bundle actually ships, or null if none match. */
    fun resolveBestAbi(supportedAbis: Array<String>, bundleAbis: Set<String> = KNOWN_ABIS.toSet()): String? {
        return supportedAbis.map { normalizeAbi(it) }.firstOrNull { it in KNOWN_ABIS && it in bundleAbis }
    }

    fun extractAbi(entryName: String): String? {
        val normalized = normalizeAbi(entryName)
        for (candidate in KNOWN_ABIS) {
            val pattern = Regex("(?:^|[._-])$candidate(?:[._-]|$)")
            if (pattern.containsMatchIn(normalized)) {
                return candidate
            }
        }
        return null
    }

    fun shouldIncludeSplit(entryName: String, bestAbi: String?): Boolean {
        if (!entryName.endsWith(".apk")) return false
        val splitAbi = extractAbi(entryName) ?: return true
        return bestAbi != null && splitAbi == normalizeAbi(bestAbi)
    }
}
