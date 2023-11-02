package org.jawbts.bigcannonsfix.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.YawControllerBlockEntity;

@Mixin(YawControllerBlockEntity.class)
public abstract class YawControllerBlockEntityMixin extends KineticBlockEntity {

    public YawControllerBlockEntityMixin(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    // overwrite
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);

        if (sequenceContext != null
                && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE) {
            BlockEntity be = level.getBlockEntity(worldPosition.above());
            if (!(be instanceof CannonMountBlockEntity cmbe)) {
                return;
            }
            float limit = (float) sequenceContext.getEffectiveValue(1);
            cmbe.onSpeedChanged(limit + 1024f);
        }
    }
}
