package com.wickedsik.personalworlds.compat;

import com.wickedsik.personalworlds.PersonalWorldsMod;
import com.wickedsik.personalworlds.config.ModConfig;
import net.minecraft.server.MinecraftServer;

//? if >=1.21 {
/*import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}

import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility layer for GameRules access.
 * <p>
 * MC 1.20.x uses: net.minecraft.world.level.GameRules with inner Key/BooleanValue/IntegerValue/GameRuleTypeVisitor
 * MC 1.21.x uses: net.minecraft.world.level.gamerules.GameRules with standalone GameRule/GameRuleTypeVisitor
 * <p>
 * Builds a pocket dimension's rule set in two steps: a copy of the overworld rules,
 * then the config overrides. Changes are applied without a server, so vanilla's
 * server-wide change callbacks (which would act on the overworld) don't fire.
 */
public final class GameRulesCompat {

    private GameRulesCompat() {
        // Utility class
    }

    //? if >=1.21 {
    /*/^* Cached lookup map: game rule name → GameRule. Built lazily once. ^/
    private static Map<String, GameRule<?>> rulesByName;
    *///?} else {
    // Cached lookup map: game rule name → GameRules.Key. Built lazily once.
    private static Map<String, GameRules.Key<?>> ruleKeysByName;
    //?}

    /**
     * Create the game rules for a pocket dimension:
     * <ol>
     *   <li>Baseline: a copy of ALL overworld game rules</li>
     *   <li>Overrides: config-specified values from dimensionGameRules</li>
     * </ol>
     *
     * @param server The Minecraft server (for reading overworld rules)
     * @return A new rule set, independent of the overworld's
     */
    public static GameRules createDimensionRules(MinecraftServer server) {
        GameRules overworldRules = server.overworld().getGameRules();

        //? if >=1.21 {
        /*GameRules rules = overworldRules.copy(server.getWorldData().enabledFeatures());
        *///?} else {
        GameRules rules = overworldRules.copy();
        //?}

        Map<String, Object> overrides = ModConfig.get().dimensionGameRules;
        if (overrides != null && !overrides.isEmpty()) {
            applyOverrides(rules, overrides);
        }
        return rules;
    }

    /**
     * Apply config overrides to a rule set.
     */
    //? if >=1.21 {
    /*@SuppressWarnings("unchecked")
    private static void applyOverrides(GameRules rules, Map<String, Object> overrides) {
        Map<String, GameRule<?>> nameMap = getOrBuildNameMap(rules);

        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String ruleName = entry.getKey();
            Object value = entry.getValue();

            GameRule<?> rule = nameMap.get(ruleName);
            if (rule == null) {
                PersonalWorldsMod.LOGGER.warn("Unknown game rule '{}' in dimensionGameRules config, skipping", ruleName);
                continue;
            }

            if (value instanceof Boolean boolVal) {
                rules.set((GameRule<Boolean>) rule, boolVal, null);
            } else if (value instanceof Number numVal) {
                rules.set((GameRule<Integer>) rule, numVal.intValue(), null);
            } else {
                PersonalWorldsMod.LOGGER.warn("Game rule '{}' has unsupported value type: {}", ruleName,
                    value.getClass().getSimpleName());
            }
        }
    }

    private static Map<String, GameRule<?>> getOrBuildNameMap(GameRules rules) {
        if (rulesByName != null) {
            return rulesByName;
        }

        Map<String, GameRule<?>> map = new HashMap<>();
        rules.visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public <T> void visit(GameRule<T> rule) {
                map.put(rule.getIdentifier().getPath(), rule);
            }
        });

        rulesByName = map;
        PersonalWorldsMod.LOGGER.debug("Built game rule name map with {} entries", map.size());
        return map;
    }
    *///?} else {
    @SuppressWarnings("unchecked")
    private static void applyOverrides(GameRules rules, Map<String, Object> overrides) {
        Map<String, GameRules.Key<?>> keyMap = getOrBuildKeyMap(rules);

        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String ruleName = entry.getKey();
            Object value = entry.getValue();

            GameRules.Key<?> key = keyMap.get(ruleName);
            if (key == null) {
                PersonalWorldsMod.LOGGER.warn("Unknown game rule '{}' in dimensionGameRules config, skipping", ruleName);
                continue;
            }

            if (value instanceof Boolean boolVal) {
                rules.getRule((GameRules.Key<GameRules.BooleanValue>) key).set(boolVal, null);
            } else if (value instanceof Number numVal) {
                rules.getRule((GameRules.Key<GameRules.IntegerValue>) key).set(numVal.intValue(), null);
            } else {
                PersonalWorldsMod.LOGGER.warn("Game rule '{}' has unsupported value type: {}", ruleName,
                    value.getClass().getSimpleName());
            }
        }
    }

    private static Map<String, GameRules.Key<?>> getOrBuildKeyMap(GameRules rules) {
        if (ruleKeysByName != null) {
            return ruleKeysByName;
        }

        Map<String, GameRules.Key<?>> map = new HashMap<>();
        rules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                map.put(key.getId(), key);
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                map.put(key.getId(), key);
            }
        });

        ruleKeysByName = map;
        PersonalWorldsMod.LOGGER.debug("Built game rule key map with {} entries", map.size());
        return map;
    }
    //?}
}
