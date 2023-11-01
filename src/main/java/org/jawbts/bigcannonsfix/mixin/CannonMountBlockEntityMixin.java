package org.jawbts.bigcannonsfix.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.utility.Lang;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;
import rbasamoyai.createbigcannons.index.CBCBlocks;

import java.util.List;
import java.util.function.Supplier;

@Mixin(CannonMountBlockEntity.class)
public abstract class CannonMountBlockEntityMixin extends KineticBlockEntity {
    @Shadow(remap = false) protected PitchOrientedContraptionEntity mountedContraption;

    @Shadow(remap = false) private float prevYaw;

    @Shadow(remap = false) private float cannonYaw;

    @Shadow(remap = false) private float prevPitch;

    @Shadow(remap = false) private float cannonPitch;

    @Shadow(remap = false) private float clientPitchDiff;

    @Shadow(remap = false) private float clientYawDiff;

    @Shadow(remap = false) private boolean running;

    @Shadow(remap = false) private float yawSpeed;

    @Unique
    protected double sequencedAngleLimitYaw = -1;
    @Unique
    protected double sequencedAngleLimitPitch = -1;
    @Unique
    protected float yawSequence = -1;
    private static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;


    public CannonMountBlockEntityMixin(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    // overwrite
    public void onSpeedChanged(float prevSpeed) {
        // YawControllerBlockEntity会调用这个方法
        if (prevSpeed >= 1024f) {
            yawSequence = prevSpeed - 1024f;
        } else {
            yawSequence = -1;
            sequencedAngleLimitYaw = -1;
            super.onSpeedChanged(prevSpeed);
        }

        if (sequenceContext != null
                && sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE) {
            sequencedAngleLimitPitch = sequenceContext.getEffectiveValue(getTheoreticalSpeed()) * 0.125f;
        } else {
            sequencedAngleLimitPitch = -1;
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void tick(CallbackInfo ci) {
        super.tick();

        if (mountedContraption != null) {
            if (!this.mountedContraption.isAlive()) {
                this.mountedContraption = null;
            }
        }

        prevYaw = cannonYaw;
        prevPitch = cannonPitch;

        boolean flag = mountedContraption != null &&
                mountedContraption.canBeTurnedByController((CannonMountBlockEntity)(Object) this);

        if (level.isClientSide) {
            clientYawDiff = flag ? clientYawDiff * 0.5f : 0;
            clientPitchDiff = flag ? clientPitchDiff * 0.5f : 0;
            sequencedAngleLimitPitch = -1;
            sequencedAngleLimitYaw = -1;
        }

        if (!running && !isVirtual()) {
            if (CBCBlocks.CANNON_MOUNT.has(getBlockState())) {
                cannonYaw = getBlockState().getValue(HORIZONTAL_FACING).toYRot();
                prevYaw = cannonYaw;
                cannonPitch = 0;
                prevPitch = 0;
            }
            return;
        }

        if (!(mountedContraption != null && mountedContraption.isStalled()) && flag) {
            float yawSpeedM = getAngularSpeedMixin(this::getYawSpeedMixin, clientYawDiff);
            float pitchSpeed = getAngularSpeedMixin(this::getSpeed, clientPitchDiff);

            if (yawSequence >= 0) {
                sequencedAngleLimitYaw = Math.abs(yawSequence * getTheoreticalYawSpeedMixin());
                yawSequence = -1;
            }

            if (sequencedAngleLimitPitch >= 0) {
                pitchSpeed = (float) Mth.clamp(pitchSpeed, -sequencedAngleLimitPitch, sequencedAngleLimitPitch);
                sequencedAngleLimitPitch = sequencedAngleLimitPitch - Math.abs(pitchSpeed);
            }
            if (sequencedAngleLimitYaw >= 0) {
                yawSpeedM = (float) Mth.clamp(yawSpeedM, -sequencedAngleLimitYaw, sequencedAngleLimitYaw);
                sequencedAngleLimitYaw = Math.max(0, sequencedAngleLimitYaw - Math.abs(yawSpeedM));
            }

            float newPitch = cannonPitch + pitchSpeed;
            cannonPitch = newPitch % 360.0f;

            float newYaw = cannonYaw + yawSpeedM;
            cannonYaw = newYaw % 360.0f;


            if (mountedContraption == null) {
                cannonPitch = 0.0f;
            } else {
                Direction dir = ((AbstractMountedCannonContraption) mountedContraption.getContraption()).initialOrientation();

                boolean flag1 = (dir.getAxisDirection() == Direction.AxisDirection.POSITIVE) == (dir.getAxis() == Direction.Axis.X);
                float cu = flag1 ? getMaxElevateMixin() : getMaxDepressMixin();
                float cd = flag1 ? -getMaxDepressMixin() : -getMaxElevateMixin();
                this.cannonPitch = Mth.clamp(newPitch % 360.0f, cd, cu);
            }
        }

        applyRotationMixin();

        ci.cancel();
    }

    @Unique
    private float getAngularSpeedMixin(Supplier<Float> sup, float clientDiff) {
        float speed = convertToAngular(sup.get()) * 0.125f;
        if (sup.get() == 0) {
            speed = 0;
        }
        if (level.isClientSide) {
            speed *= ServerSpeedProvider.get();
            speed += clientDiff / 3.0f;
        }
        return speed;
    }

    @Unique
    private float getYawSpeedMixin() {
        return overStressed ? 0 : this.getTheoreticalYawSpeedMixin();
    }

    @Unique
    private float getTheoreticalYawSpeedMixin() {
        return yawSpeed;
    }

    @Unique
    protected void applyRotationMixin() {
        if (mountedContraption == null) return;
        if (!mountedContraption.canBeTurnedByController((CannonMountBlockEntity)(Object) this)) {
            cannonPitch = mountedContraption.pitch;
            cannonYaw = mountedContraption.yaw;
        } else {
            mountedContraption.pitch = cannonPitch;
            mountedContraption.yaw = cannonYaw;
        }
    }

    @Unique
    private float getMaxDepressMixin() {
        return mountedContraption.maximumDepression();
    }

    @Unique
    private float getMaxElevateMixin() {
        return mountedContraption.maximumElevation();
    }

    // overwrite
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);


        Lang.number(cannonPitch).forGoggles(tooltip, 1);

        return true;
    }
}
