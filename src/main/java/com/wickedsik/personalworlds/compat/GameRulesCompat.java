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
import xyz.nucleoid.fantasy.RuntimeWorldConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility layer for GameRules access.
 * <p>
 * MC 1.20.x uses: net.minecraft.world.level.GameRules with inner Key/BooleanValue/IntegerValue/GameRuleTypeVisitor
 * MC 1.21.x uses: net.minecraft.world.level.gamerules.GameRules with standalone GameRule/GameRuleTypeVisitor
 * <p>
 * Fantasy API also differs:
 * 1.20.x: setGameRule(GameRules.Key&lt;BooleanValue&gt;, boolean) / setGameRule(GameRules.Key&lt;IntegerValue&gt;, int)
 * 1.21.x: setGameRule(GameRule&lt;T&gt;, T) (generic)
 * <p>
 * Provides two-phase game rule application: baseline from overworld, then config overrides.
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
     * Apply game rules to a RuntimeWorldConfig using a two-phase approach:
     * <ol>
     *   <li>Baseline: Copy ALL overworld game rules to the config</li>
     *   <li>Overrides: Apply config-specified overrides from dimensionGameRules</li>
     * </ol>
     *
     * @param config The Fantasy RuntimeWorldConfig to modify
     * @param server The Minecraft server (for reading overworld rules)
     */
    public static void applyGameRules(RuntimeWorldConfig config, MinecraftServer server) {
        GameRules overworldRules = server.overworld().getGameRules();

        // Phase 1: Copy all overworld rules as baseline
        copyAllRules(config, overworldRules);

        // Phase 2: Apply config overrides
        Map<String, Object> overrides = ModConfig.get().dimensionGameRules;
        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        applyOverrides(config, overworldRules, overrides);
    }

    /**
     * Copy all game rules from the overworld to a RuntimeWorldConfig.
     */
    //? if >=1.21 {
    /*private static void copyAllRules(RuntimeWorldConfig config, GameRules overworldRules) {
        overworldRules.visitGameRuleTypes(new GameRuleTypeVisitor() {
            @Override
            public <T> void visit(GameRule<T> rule) {
                T value = overworldRules.get(rule);
                config.setGameRule(rule, value);
            }
        });
    }
    *///?} else {
    private static void copyAllRules(RuntimeWorldConfig config, GameRules overworldRules) {
        overworldRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanValue> key, GameRules.Type<GameRules.BooleanValue> type) {
                config.setGameRule(key, overworldRules.getRule(key).get());
            }

            @Override
            public void visitInteger(GameRules.Key<GameRules.IntegerValue> key, GameRules.Type<GameRules.IntegerValue> type) {
                config.setGameRule(key, overworldRules.getRule(key).get());
            }
        });
    }
    //?}

    /**
     * Apply config overrides to the RuntimeWorldConfig.
     */
    //? if >=1.21 {
    /*@SuppressWarnings("unchecked")
    private static void applyOverrides(RuntimeWorldConfig config, GameRules overworldRules, Map<String, Object> overrides) {
        Map<String, GameRule<?>> nameMap = getOrBuildNameMap(overworldRules);

        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String ruleName = entry.getKey();
            Object value = entry.getValue();

            GameRule<?> rule = nameMap.get(ruleName);
            if (rule == null) {
                PersonalWorldsMod.LOGGER.warn("Unknown game rule '{}' in dimensionGameRules config, skipping", ruleName);
                continue;
            }

            if (value instanceof Boolean boolVal) {
                config.setGameRule((GameRule<Boolean>) rule, boolVal);
            } else if (value instanceof Number numVal) {
                config.setGameRule((GameRule<Integer>) rule, numVal.intValue());
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
    private static void applyOverrides(RuntimeWorldConfig config, GameRules overworldRules, Map<String, Object> overrides) {
        Map<String, GameRules.Key<?>> keyMap = getOrBuildKeyMap(overworldRules);

        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String ruleName = entry.getKey();
            Object value = entry.getValue();

            GameRules.Key<?> key = keyMap.get(ruleName);
            if (key == null) {
                PersonalWorldsMod.LOGGER.warn("Unknown game rule '{}' in dimensionGameRules config, skipping", ruleName);
                continue;
            }

            if (value instanceof Boolean boolVal) {
                config.setGameRule((GameRules.Key<GameRules.BooleanValue>) key, boolVal);
            } else if (value instanceof Number numVal) {
                config.setGameRule((GameRules.Key<GameRules.IntegerValue>) key, numVal.intValue());
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
