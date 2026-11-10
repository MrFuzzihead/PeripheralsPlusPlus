package com.austinv11.peripheralsplusplus.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import javazoom.jl.decoder.JavaLayerException;

import com.gtranslate.Audio;
import com.gtranslate.Language;
import com.gtranslate.Translator;

public class TranslateUtils {

    /**
     * Reverse-looks-up a language prefix from the English name of a language ({@code "English"} -> {@code "en"}).
     *
     * <p>
     * This used to read {@code Language.hashLanguage} reflectively; the field now has a getter, so no reflection is
     * involved.
     */
    public static String getLangFromWord(String lang) {
        HashMap<String, String> hashLanguage = Language.getInstance()
            .getHashLanguage();
        for (String k : hashLanguage.keySet()) if (hashLanguage.get(k)
            .equalsIgnoreCase(lang)) return k;
        return null;
    }

    public static String translate(String text, String fromLang, String toLang) {
        return Translator.getInstance()
            .translate(text, fromLang, toLang);
    }

    public static String translate(String text, String toLang) {
        return translate(
            text,
            Translator.getInstance()
                .detect(text),
            toLang);
    }

    public static boolean isPrefix(String lang) {
        return lang.length() == 2 || lang.toLowerCase()
            .contains("zh-");
    }

    public static String detectLangPrefix(String text) {
        return Translator.getInstance()
            .detect(text);
    }

    public static void playAudio(String text, String lang) throws IOException, JavaLayerException {
        Audio audio = Audio.getInstance();
        InputStream sound = audio.getAudio(text, lang);
        audio.play(sound);
    }
}
