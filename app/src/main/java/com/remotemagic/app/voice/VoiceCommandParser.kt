package com.remotemagic.app.voice

import com.remotemagic.app.domain.RemoteKey
import com.remotemagic.app.domain.TvApp

sealed interface VoiceCommand {
    data class Key(val key: RemoteKey) : VoiceCommand
    data class Open(val app: TvApp) : VoiceCommand
    data object PowerOn : VoiceCommand
}

/** Turns an Arabic, French or English sentence into one TV command. */
object VoiceCommandParser {
    fun normalize(text: String): String {
        val latin = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        return latin.replace(Regex("\\p{Mn}+"), "") // French accents and Arabic harakat / hamza marks
            .replace('ى', 'ي').replace('ة', 'ه').replace('ٱ', 'ا')
            .replace(Regex("[^\\p{L}\\p{N}+ ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    // Order matters: more specific phrases come first.
    private val rules: List<Pair<VoiceCommand, List<String>>> = listOf(
        VoiceCommand.Open(TvApp.YOUTUBE) to listOf("youtube", "you tube", "يوتيوب"),
        VoiceCommand.Open(TvApp.IPTV) to listOf("iptv", "اي بي تي في"),
        VoiceCommand.Open(TvApp.RADIO) to listOf("radio", "الراديو", "راديو"),
        VoiceCommand.Key(RemoteKey.CHANNEL_UP) to listOf("next channel", "channel up", "chaine suivante", "القناه التاليه", "القناه الموالية", "القناه المواليه"),
        VoiceCommand.Key(RemoteKey.CHANNEL_DOWN) to listOf("previous channel", "channel down", "chaine precedente", "القناه السابقه"),
        VoiceCommand.Key(RemoteKey.MUTE) to listOf("mute", "silence", "coupe le son", "اغلق الصوت", "اكتم", "كتم الصوت"),
        VoiceCommand.Key(RemoteKey.VOLUME_UP) to listOf("volume up", "louder", "monte le son", "augmente le volume", "augmente le son", "ارفع الصوت", "زد الصوت"),
        VoiceCommand.Key(RemoteKey.VOLUME_DOWN) to listOf("volume down", "quieter", "baisse le son", "baisse le volume", "diminue le volume", "اخفض الصوت", "انقص الصوت"),
        VoiceCommand.Key(RemoteKey.HOME) to listOf("home", "accueil", "الصفحه الرئيسيه", "الرئيسيه"),
        VoiceCommand.Key(RemoteKey.SETTINGS) to listOf("settings", "parametres", "reglages", "الاعدادات"),
        VoiceCommand.PowerOn to listOf("turn on", "power on", "wake", "allume", "شغل التلفاز", "تشغيل التلفاز", "شغل التلفزيون", "افتح التلفاز"),
        VoiceCommand.Key(RemoteKey.POWER) to listOf("turn off", "power off", "eteins", "eteindre", "اوقف التلفاز", "اطفئ التلفاز", "اغلق التلفاز", "اطفي التلفاز"),
        VoiceCommand.Key(RemoteKey.BACK) to listOf("go back", "back", "retour", "رجوع", "للخلف"),
        VoiceCommand.Key(RemoteKey.PLAY_PAUSE) to listOf("pause", "play", "lecture", "ايقاف مؤقت", "توقف", "تشغيل")
    )

    fun parse(sentence: String): VoiceCommand? {
        val text = " ${normalize(sentence)} "
        return rules.firstOrNull { (_, phrases) ->
            phrases.any { phrase ->
                val p = normalize(phrase)
                // Arabic prefixes/suffixes attach to words, so match substrings there.
                if (p.any { it in '؀'..'ۿ' }) text.contains(p) else text.contains(" $p ")
            }
        }?.first
    }
}
