package com.austinv11.peripheralsplusplus.tiles;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.austinv11.peripheralsplusplus.reference.Config;

/**
 * Guards the shape of the config class.
 *
 * <p>
 * CollectiveFramework's {@code ConfigRegistry} reflects over the fields, so a field that is not {@code public static}
 * silently disappears from the config file, and a {@code static final} one makes the registry throw. Both had happened
 * in this mod's history (CODE_REVIEW.md sections 2.2 and 6.10).
 * </p>
 */
public class ConfigSurfaceTest {

    private static List<Field> configFields() {
        List<Field> fields = new ArrayList<Field>();
        for (Field field : Config.class.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            // ENABLE_CONFIG_MESSAGE is a private shared help string, not an option; it is excluded from the file.
            if (field.getName()
                .equals("ENABLE_CONFIG_MESSAGE")) continue;
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers)) fields.add(field);
        }
        return fields;
    }

    @Test
    public void everyConfigFieldIsPublicStaticAndMutable() {
        for (Field field : configFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                Assert.fail(
                    "config field " + field.getName() + " must be public static to be editable from the config file");
            }
            Assert.assertFalse(
                "config field " + field.getName() + " must not be final (it cannot be assigned)",
                Modifier.isFinal(modifiers));
        }
    }

    @Test
    public void everyConfigFieldHasATypeTheRegistrySupports() {
        for (Field field : configFields()) {
            Class<?> type = field.getType();
            boolean supported = type == boolean.class || type == int.class
                || type == double.class
                || type == float.class
                || type == long.class
                || type == String.class;
            Assert.assertTrue("config field " + field.getName() + " has unsupported type " + type, supported);
        }
    }

    @Test
    public void everyConfigFieldHasADescription() {
        for (Field field : configFields()) {
            Assert.assertNotNull(
                "config field " + field.getName() + " has no @Description and will not appear in the config file",
                field.getAnnotation(com.austinv11.collectiveframework.minecraft.config.Description.class));
        }
    }

    @Test
    public void theFieldsHaveSaneDefaults() {
        Assert.assertTrue(Config.maxNumberOfPeripherals > 0);
        Assert.assertTrue(Config.maxNumberOfMillibuckets > 0);
        Assert.assertTrue(Config.sensorRange > 0);
        Assert.assertTrue(Config.sayRange > 0);
        Assert.assertTrue(Config.speechRange > 0);
        Assert.assertTrue(Config.noteBlockRange > 0);
        Assert.assertTrue(Config.teleporterPenalty >= 1.0d);
        Assert.assertTrue(Config.chunkLoadingRadius >= 0);
        Assert.assertTrue(Config.sayRate >= 0);
    }

    @Test
    public void theDefaultStateIsEverythingEnabled() {
        // A fresh install should expose every peripheral; the review pass removed the one dead option that was
        // excluded from the file entirely.
        Assert.assertTrue(Config.enableChatBox);
        Assert.assertTrue(Config.enableSpeaker);
        Assert.assertTrue(Config.enablePeripheralContainer);
        Assert.assertTrue(Config.enableInteractiveSorter);
        Assert.assertTrue(Config.enablePlayerInterface);
    }

    @Test
    public void noConfigCommentContainsAFormatSpecifier() {
        // CollectiveFramework passes these straight to String.format when it logs a parse warning, so a "%" in a
        // description used to throw UnknownFormatConversionException on every startup (CODE_REVIEW.md section 16).
        for (Field field : configFields()) {
            com.austinv11.collectiveframework.minecraft.config.Description description = field
                .getAnnotation(com.austinv11.collectiveframework.minecraft.config.Description.class);
            if (description == null) continue;
            Assert.assertFalse(
                "config comment for " + field.getName() + " contains a '%', which breaks the config logger",
                description.comment()
                    .contains("%"));
        }
    }
}
