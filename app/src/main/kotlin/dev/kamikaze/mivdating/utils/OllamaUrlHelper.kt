package dev.kamikaze.mivdating.utils

import android.content.Context
import android.os.Build
import java.net.NetworkInterface

/**
 * Утилита для определения адреса Ollama сервера
 * Поддерживает работу оффлайн с локальным Ollama
 */
object OllamaUrlHelper {
    
    /**
     * Определяет адрес Ollama в зависимости от типа устройства
     * - Эмулятор: http://130.49.153.154:8000
     * - Реальное устройство: пытается найти локальный IP компьютера
     */
    fun getDefaultOllamaUrl(context: Context): String {
        return if (isEmulator()) {
            "http://10.0.2.2:8000"
        } else {
            // Для реального устройства пытаемся найти локальный IP
            // Пользователь может настроить адрес вручную
            getLocalNetworkIp()?.let { "http://$it:11434" } 
                ?: "http://192.168.1.1:11434" // Дефолтный адрес, пользователь должен настроить
        }
    }
    
    /**
     * Проверяет, запущено ли приложение на эмуляторе
     */
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
    
    /**
     * Получает локальный IP адрес устройства в локальной сети
     * Используется для подключения к Ollama на компьютере в той же сети
     */
    private fun getLocalNetworkIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    // Игнорируем loopback и не-IPv4 адреса
                    if (address.isLoopbackAddress || address.hostAddress == null) {
                        continue
                    }
                    
                    val hostAddress = address.hostAddress ?: continue
                    
                    // Проверяем, что это локальный IPv4 адрес
                    if (hostAddress.startsWith("192.168.") 
                        || hostAddress.startsWith("10.")
                        || hostAddress.startsWith("172.16.") 
                        || hostAddress.startsWith("172.17.")
                        || hostAddress.startsWith("172.18.")
                        || hostAddress.startsWith("172.19.")
                        || hostAddress.startsWith("172.20.")
                        || hostAddress.startsWith("172.21.")
                        || hostAddress.startsWith("172.22.")
                        || hostAddress.startsWith("172.23.")
                        || hostAddress.startsWith("172.24.")
                        || hostAddress.startsWith("172.25.")
                        || hostAddress.startsWith("172.26.")
                        || hostAddress.startsWith("172.27.")
                        || hostAddress.startsWith("172.28.")
                        || hostAddress.startsWith("172.29.")
                        || hostAddress.startsWith("172.30.")
                        || hostAddress.startsWith("172.31.")) {
                        return hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Получает IP адрес компьютера для подключения с реального устройства
     * Показывает инструкции пользователю
     */
    fun getConnectionInstructions(context: Context): String {
        return if (isEmulator()) {
            """
            📱 Эмулятор Android

            Режимы подключения:

            1. Локальный Ollama:
               - Адрес: http://130.49.153.154:8000
               - Ollama запущен на вашем компьютере
               - Команды:
                 ollama serve
                 ollama pull qwen2.5:14b

            2. Удаленный сервер:
               - Адрес: http://YOUR_SERVER_IP:8000
               - Flask API сервер на удаленной машине
               - См. REMOTE_SERVER_SETUP.md для настройки
            """.trimIndent()
        } else {
            val localIp = getLocalNetworkIp() ?: "YOUR_COMPUTER_IP"
            """
            📱 Реальное устройство

            Режимы подключения:

            1. Локальный Ollama в той же сети:
               - Найдите IP компьютера: ipconfig / ifconfig
               - Запустите: OLLAMA_HOST=0.0.0.0 ollama serve
               - Адрес: http://COMPUTER_IP:11434
               - IP устройства: $localIp

            2. Удаленный сервер:
               - Адрес: http://SERVER_IP:8000
               - Настройте сервер по инструкции REMOTE_SERVER_SETUP.md
            """.trimIndent()
        }
    }

    /**
     * URL удаленного сервера по умолчанию
     */
    const val REMOTE_SERVER_URL = "http://130.49.153.154:8000"
}

