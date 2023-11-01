package org.jawbts.bigcannonsfix.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.YawControllerBlockEntity;

@Mixin(YawControllerBlockEntity.class)
public abstract class YawControllerBlockEntityMixin extends KineticBlockEntity {
    @Shadow(remap = false) public abstract @Nullable CannonMountBlockEntity getCannonMount();

    public YawControllerBlockEntityMixin(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    // overwrite
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);

        if (sequenceContext != null
                && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE) {
            CannonMountBlockEntity cmbe = getCannonMount();
            if (cmbe != null) {
                float limit = (float) sequenceContext.getEffectiveValue(1);
                cmbe.onSpeedChanged(limit + 1024f);
            }
        }
    }
}
