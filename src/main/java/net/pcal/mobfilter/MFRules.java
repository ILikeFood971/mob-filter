package net.pcal.mobfilter;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.level.ServerWorldProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static net.pcal.mobfilter.MFRules.FilterRuleAction.ALLOW_SPAWN;

@SuppressWarnings("SpellCheckingInspection")
abstract class MFRules {

    private static final Logger LOGGER = LogManager.getLogger(MFRules.class);

    public static class FilterRuleList {
        private final List<FilterRule> rules;

        FilterRuleList(List<FilterRule> rules) {
            this.rules = requireNonNull(rules);
        }

        public boolean isSpawnAllowed(SpawnRequest request) {
            request.logger().trace(() -> "[MobFilter] IS_SPAWN_ALLOWED " + request);
            for (FilterRule rule : rules) {
                request.logger().trace(() -> "[MobFilter]   RULE '" + rule.ruleName + "'");
                Boolean isSpawnAllowed = rule.isSpawnAllowed(request);
                if (isSpawnAllowed != null) {
                    request.logger().trace(() -> "[MobFilter]   RETURN " + isSpawnAllowed);
                    return isSpawnAllowed;
                }
            }
            request.logger().trace(() -> "[MobFilter]   RETURN true (no rules matched)");
            return true;
        }

        public int getSize() {
            return this.rules.size();
        }

    }


    public enum FilterRuleAction {
        ALLOW_SPAWN,
        DISALLOW_SPAWN
    }

    /**
     * One rule to be evaluated in the filter chain.
     */
    record FilterRule(String ruleName,
                      Collection<FilterCheck> checks,
                      FilterRuleAction action) {

        FilterRule {
            requireNonNull(ruleName);
            requireNonNull(checks);
            requireNonNull(action);
        }

        /**
         * Return whether the requested spawn should be allowed, or null if we don't have any opinion (i.e., because
         * the rule didn't match).
         */
        public Boolean isSpawnAllowed(SpawnRequest request) {
            for (final FilterCheck check : checks) {
                if (!check.isMatch(request)) return null;
            }
            return this.action == ALLOW_SPAWN;
        }

    }

    /**
     * Encapsualtes the parameters in a minecraft call to 'canSpawn'.
     */
    record SpawnRequest(ServerWorld serverWorld,
                        SpawnGroup spawnGroup,
                        EntityType<?> entityType,
                        BlockPos blockPos,
                        Logger logger) {

        /**
         * Return the entity id of the mob that is going to spawn.
         */
        public String getEntityId() {
            return String.valueOf(Registries.ENTITY_TYPE.getId(entityType)); // FIXME is this right?
        }

        /**
         * Return the name of the world that the spawn is happening in.
         */
        public String getWorldName() {
            final ServerWorldProperties swp;
            try {
                swp = (ServerWorldProperties) this.serverWorld.getLevelProperties();
            } catch (ClassCastException cce) {
                LOGGER.warn("[MobFilter] serverWorld.getLevelProperties() is unexpected class: " +
                        this.serverWorld.getLevelProperties().getClass().getName());
                return null;
            }
            return swp.getLevelName();
        }

        /**
         * Return the id of the dimension that the spawn is happening in.
         */
        public String getDimensionId() {
            return this.serverWorld.getRegistryKey().getValue().toString();
        }

        /**
         * Return the id of the biome that the spawn is happening in.
         */
        public String getBiomeId() {
            final Biome biome = serverWorld.getBiome(this.blockPos()).value();
            // FIXME? I'm not entirely sure this is correct
            return String.valueOf(serverWorld.getRegistryManager().get(RegistryKeys.BIOME).getId(biome));
        }

        /**
         * Return the id of the block that the spawn is happening on.
         */
        public String getBlockId() {
            final BlockState bs = serverWorld.getBlockState(this.blockPos.down());
            final Block block = bs.getBlock();
            return String.valueOf(Registries.BLOCK.getId(block));
        }
    }

    /**
     * Encapsulates a single condition check of a rule.
     */
    interface FilterCheck {
        boolean isMatch(SpawnRequest spawn);
    }

    // TODO seems like we could optimize by completely dropping rules with a worldName check that won't match
    // the current running world
    record WorldNameCheck(StringSet worldNames) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            boolean isMatch = worldNames.contains(req.getWorldName());
            req.logger().trace(() -> "[MobFilter]     WorldNameCheck: " + req.getWorldName() + " in " + worldNames + " " + isMatch);
            return isMatch;
        }
    }

    record DimensionCheck(StringSet dimensionIds) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            boolean isMatch = this.dimensionIds.contains(req.getDimensionId());
            req.logger().trace(() -> "[MobFilter]     DimensionCheck " + req.getDimensionId() + " in " + dimensionIds + " " + isMatch);
            return isMatch;
        }
    }

    record BiomeCheck(StringSet biomeIds) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            boolean isMatch = this.biomeIds.contains(req.getBiomeId());
            req.logger().trace(() -> "[MobFilter]     BiomeCheck " + req.getBiomeId() + " in " + biomeIds+" "+isMatch);
            return isMatch;
        }
    }
    record SpawnGroupCheck(EnumSet<SpawnGroup> groups) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            boolean isMatch = this.groups.contains(req.spawnGroup);
            req.logger().trace(() -> "[MobFilter]     SpawnGroupCheck: " + this.groups + " " + req.spawnGroup + " " + this.groups.contains(req.spawnGroup) + " " + isMatch);
            return isMatch;
        }
    }

    record EntityIdCheck(StringSet entityIds) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            boolean isMatch = this.entityIds.contains(req.getEntityId());
            req.logger().trace(() -> "[MobFilter]     EntityNameCheck " + req.getEntityId() + " in " + entityIds+" "+isMatch);
            return isMatch;
        }
    }

    record BlockIdCheck(StringSet blockIds) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            boolean isMatch = this.blockIds.contains(req.getBlockId());
            req.logger().trace(() -> "[MobFilter]     BlockIdCheck " + req.getEntityId() + " in " + blockIds+" "+isMatch);
            return isMatch;
        }
    }

    record BlockPosCheck(Direction.Axis axis, int min, int max) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            int val = req.blockPos.getComponentAlongAxis(this.axis);
            boolean isMatch = min <= val && val <= max;
            req.logger().trace(() -> "[MobFilter]     BlockPosCheck " + axis + " " + min + " <= " + val + " <= " + max+ " "+isMatch);
            return isMatch;
        }
    }


    record LightLevelCheck(int min, int max) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            int val = req.serverWorld().getLightLevel(req.blockPos);
            boolean isMatch = min <= val && val <= max;
            req.logger().trace(() -> "[MobFilter]     LightLevelCheck " + min + " <= " + val + " <= " + max+ " " +isMatch);
            return isMatch;

        }
    }

    record TimeOfDayCheck(long min, long max) implements FilterCheck {
        @Override
        public boolean isMatch(SpawnRequest req) {
            long val = req.serverWorld.getTimeOfDay();
            boolean isMatch = min <= val && val <= max;
            req.logger().trace(() -> "[MobFilter]     TimeOfDayCheck " + min + " <= " + val + " <= " + max+" "+isMatch);
            return isMatch;
        }
    }

    /**
     * An immutable set of strings with membership testing.  These gets used a lot and may be
     * in need of optimization.
     */
    @SuppressWarnings("ClassCanBeRecord")
    public static class StringSet {
        private final String[] strings;

        public static StringSet of(String[] strings) {
            return new StringSet(strings);
        }

        private StringSet(String[] strings) {
            this.strings = requireNonNull(strings);
        }

        public boolean contains(String value) {
            for (String a : this.strings) if (Objects.equals(a, value)) return true;
            return false;
        }

        public String toString() {
            return Arrays.toString(this.strings);
        }
    }
}


