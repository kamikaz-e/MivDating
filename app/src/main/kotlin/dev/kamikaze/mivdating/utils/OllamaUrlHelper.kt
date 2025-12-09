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
     * - Эмулятор: http://10.0.2.2:11434
     * - Реальное устройство: пытается найти локальный IP компьютера
     */
    fun getDefaultOllamaUrl(context: Context): String {
        return if (isEmulator()) {
            "http://10.0.2.2:11434"
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
            
            Ollama должен быть запущен на вашем компьютере.
            Адрес подключения: http://10.0.2.2:11434
            
            Убедитесь, что:
            1. Ollama запущен: ollama serve
            2. Модель установлена: ollama pull qwen2.5:14b
            """.trimIndent()
        } else {
            val localIp = getLocalNetworkIp() ?: "YOUR_COMPUTER_IP"
            """
            📱 Реальное устройство
            
            Для работы оффлайн:
            1. Убедитесь, что устройство и компьютер в одной Wi-Fi сети
            2. Найдите IP адрес вашего компьютера:
               - Windows: ipconfig
               - Mac/Linux: ifconfig или ip addr
            3. Запустите Ollama с доступом из сети:
               OLLAMA_HOST=0.0.0.0 ollama serve
            4. Введите IP адрес компьютера в настройках
            
            Текущий IP устройства: $localIp
            """.trimIndent()
        }
    }
}

