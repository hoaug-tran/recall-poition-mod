package com.hoaug.recall;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.function.Consumer;

public class DeathRecallPotionItem extends Item {
    public DeathRecallPotionItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!(user instanceof ServerPlayerEntity player)) {
            return stack;
        }

        Optional<GlobalPos> deathPosOpt = player.getLastDeathPos();
        if (deathPosOpt.isEmpty()) {
            player.sendMessage(
                    Text.translatable("message.recall_potion.death_fail").formatted(Formatting.RED),
                    true);
            world.playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                    SoundCategory.PLAYERS,
                    1.0f,
                    0.5f);
            return stack;
        }

        GlobalPos deathPos = deathPosOpt.get();
        ServerWorld targetWorld = player.getServer().getWorld(deathPos.dimension());

        if (targetWorld == null) {
            player.sendMessage(
                    Text.translatable("message.recall_potion.death_fail").formatted(Formatting.RED),
                    true);
            return stack;
        }

        BlockPos dest = findSafeDestination(targetWorld, deathPos.pos());

        TeleportTarget teleportTarget = new TeleportTarget(
                targetWorld,
                new Vec3d(dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D),
                Vec3d.ZERO,
                player.getYaw(),
                player.getPitch(),
                TeleportTarget.NO_OP);

        // Play sound at original location before teleport
        world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f);

        player.teleportTo(teleportTarget);

        // Play sound at new location after teleport
        targetWorld.playSound(
                null,
                dest,
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                SoundCategory.PLAYERS,
                1.0f,
                1.0f);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0)); // 5 seconds
        targetWorld.spawnParticles(net.minecraft.particle.ParticleTypes.REVERSE_PORTAL, dest.getX() + 0.5D,
                dest.getY() + 1.0D, dest.getZ() + 0.5D, 50, 0.5D, 1.0D, 0.5D, 0.1D);

        player.sendMessage(
                Text.translatable("message.recall_potion.death_success").formatted(Formatting.GREEN),
                true);

        player.getItemCooldownManager().set(stack, 100);

        if (!player.isCreative()) {
            stack.decrement(1);
        }

        return stack;
    }

    private static BlockPos findSafeDestination(ServerWorld world, BlockPos start) {
        BlockPos dest = start;

        for (int i = 0; i < 10; i++) {
            if (world.getBlockState(dest).isAir() && world.getBlockState(dest.up()).isAir()) {
                return dest;
            }
            dest = dest.up();
        }

        return dest;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 40;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplayComponent displayComponent,
            Consumer<Text> textConsumer,
            TooltipType type) {
        textConsumer.accept(
                Text.translatable("tooltip.recall_potion.death").formatted(Formatting.GRAY));
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient() || !(user instanceof ServerPlayerEntity player)) {
            return;
        }

        if (player.hurtTime > 0) {
            player.clearActiveItem();
            player.sendMessage(
                    Text.translatable("message.recall_potion.interrupted").formatted(Formatting.RED),
                    true);
            return;
        }

        if (remainingUseTicks == 39 && world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER,
                    SoundCategory.PLAYERS,
                    0.3f, 2.0f);
        }

        if (remainingUseTicks % 10 == 0 && world instanceof ServerWorld serverWorld) {
            serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK.value(),
                    SoundCategory.PLAYERS,
                    0.5f, world.random.nextFloat() * 0.1F + 0.9F);
        }

        if (remainingUseTicks % 5 == 0 && world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    10,
                    0.5D,
                    0.5D,
                    0.5D,
                    0.0D);
        }
    }
}