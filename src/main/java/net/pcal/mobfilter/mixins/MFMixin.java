package net.pcal.mobfilter.mixins;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.pcal.mobfilter.MFService;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("ALL")
@Mixin(SpawnHelper.class)
public abstract class MFMixin {

    @Inject(method = "canSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/SpawnSettings$SpawnEntry;Lnet/minecraft/util/math/BlockPos$Mutable;D)Z", at = @At("RETURN"), cancellable = true)
    private static void canSpawn_inject(ServerWorld sw,
                                        SpawnGroup sg,
                                        StructureAccessor sa,
                                        ChunkGenerator cg,
                                        SpawnSettings.SpawnEntry se,
                                        BlockPos.Mutable pos,
                                        double sd,
                                        CallbackInfoReturnable<Boolean> returnable) {
        if (returnable.getReturnValue() == true) { // if minecraft code decided it canSpawn...
            // ...call our service to decide if we want to veto the spawn
            final boolean isSpawnAllowed = MFService.getInstance().isSpawnAllowed(sw, sg, se, pos);
            // and change the return value if so
            if (!isSpawnAllowed) returnable.setReturnValue(false);
        }
    }

    @Inject(method = "canSpawn(Lnet/minecraft/entity/SpawnRestriction$Location;Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/EntityType;)Z", at = @At("RETURN"), cancellable = true)
    private static void noSpawnIfDisabled(SpawnRestriction.Location location, WorldView world, BlockPos pos, @Nullable EntityType<?> entityType, CallbackInfoReturnable<Boolean> returnable) {
        if (returnable.getReturnValue() == true) {
            final boolean isSpawnAllowed = MFService.getInstance().isSpawnAllowed(world, pos, entityType);

            if (!isSpawnAllowed) returnable.setReturnValue(false);
        }
    }

}
