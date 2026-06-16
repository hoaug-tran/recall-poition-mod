package com.hoaug.recall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RecallMod implements ModInitializer {
	public static final String MOD_ID = "recall_potion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static class TpaRequest {
		public final UUID source;
		public final long time;

		public TpaRequest(UUID source) {
			this.source = source;
			this.time = System.currentTimeMillis();
		}
	}

	public static final Map<UUID, TpaRequest> tpaRequests = new HashMap<>();
	public static final Map<UUID, Long> htpCooldowns = new HashMap<>();

	public static final Item HOME_POTION = new HomeRecallPotionItem(new Item.Settings()
			.registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "home"))).maxCount(16));
	public static final Item DEATH_POTION = new DeathRecallPotionItem(new Item.Settings()
			.registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "death"))).maxCount(16));

	@Override
	public void onInitialize() {
		RecallConfig.load();

		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "home"), HOME_POTION);
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "death"), DEATH_POTION);

		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
			content.add(new ItemStack(HOME_POTION));
			content.add(new ItemStack(DEATH_POTION));
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(literal("sethome").executes(context -> {
				ServerPlayerEntity player = context.getSource().getPlayerOrThrow();

				RecallState state = RecallState.getServerState(context.getSource().getServer());
				long lastTime = state.getLastSetHomeTime(player.getUuid());
				long now = System.currentTimeMillis() / 1000L;

				if (now - lastTime < RecallConfig.INSTANCE.setHomeCooldownSeconds) {
					long remaining = RecallConfig.INSTANCE.setHomeCooldownSeconds - (now - lastTime);
					long h = remaining / 3600;
					long m = (remaining % 3600) / 60;
					long s = remaining % 60;
					
					StringBuilder timeStr = new StringBuilder();
					if (h > 0) timeStr.append(h).append(" giờ ");
					if (m > 0) timeStr.append(m).append(" phút ");
					if (s > 0 || timeStr.length() == 0) timeStr.append(s).append(" giây");
					
					player.sendMessage(
							Text.literal("§cBạn phải chờ " + timeStr.toString().trim() + " nữa mới được đổi vị trí nhà!"),
							false);
					return 0;
				}

				BlockPos pos = player.getBlockPos();
				World world = player.getWorld();

				state.setHome(player.getUuid(), GlobalPos.create(world.getRegistryKey(), pos));
				player.sendMessage(
						Text.translatable("message.recall_potion.set_home", pos.getX(), pos.getY(), pos.getZ())
								.formatted(Formatting.GREEN),
						false);

				return 1;
			}));

			dispatcher.register(literal("recalladmin")
					.requires(source -> source.hasPermissionLevel(2))
					.then(literal("reload").executes(context -> {
						RecallConfig.load();
						context.getSource().sendMessage(Text.literal("§aRecall config reloaded."));
						return 1;
					}))
					.then(literal("setcooldown")
							.then(argument("seconds", IntegerArgumentType.integer(0)).executes(context -> {
								int secs = IntegerArgumentType.getInteger(context, "seconds");
								RecallConfig.INSTANCE.setHomeCooldownSeconds = secs;
								RecallConfig.save();
								context.getSource().sendMessage(
										Text.literal("§aSet home cooldown to " + secs + " seconds."));
								return 1;
							})))
					.then(literal("sethtpcooldown")
							.then(argument("seconds", IntegerArgumentType.integer(0)).executes(context -> {
								int secs = IntegerArgumentType.getInteger(context, "seconds");
								RecallConfig.INSTANCE.htpCooldownSeconds = secs;
								RecallConfig.save();
								context.getSource().sendMessage(
										Text.literal("§aSet HTP cooldown to " + secs + " seconds."));
								return 1;
							})))
					.then(literal("sethtptimeout")
							.then(argument("seconds", IntegerArgumentType.integer(0)).executes(context -> {
								int secs = IntegerArgumentType.getInteger(context, "seconds");
								RecallConfig.INSTANCE.htpTimeoutSeconds = secs;
								RecallConfig.save();
								context.getSource().sendMessage(
										Text.literal("§aSet HTP request timeout to " + secs + " seconds."));
								return 1;
							})))
					.then(literal("settpacost")
							.then(argument("cost", DoubleArgumentType.doubleArg(0)).executes(context -> {
								double cost = DoubleArgumentType.getDouble(context, "cost");
								RecallConfig.INSTANCE.tpaCost = cost;
								RecallConfig.save();
								context.getSource().sendMessage(
										Text.literal("§aSet TPA cost to " + cost));
								return 1;
							})))
					.then(literal("reset")
							.then(literal("sethome")
									.then(argument("player", EntityArgumentType.player()).executes(context -> {
										ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
										RecallState.getServerState(context.getSource().getServer()).resetSetHomeTime(target.getUuid());
										context.getSource().sendMessage(Text.literal("§aReset sethome cooldown for " + target.getName().getString()));
										return 1;
									})))
							.then(literal("htp")
									.then(argument("player", EntityArgumentType.player()).executes(context -> {
										ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
										htpCooldowns.remove(target.getUuid());
										context.getSource().sendMessage(Text.literal("§aReset HTP cooldown for " + target.getName().getString()));
										return 1;
									})))));

			dispatcher.register(literal("htp")
					.then(argument("target", EntityArgumentType.player()).executes(context -> {
						ServerPlayerEntity source = context.getSource().getPlayerOrThrow();
						ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "target");

						if (source.getUuid().equals(target.getUuid())) {
							source.sendMessage(
									Text.translatable("message.recall_potion.htp.self")
											.formatted(Formatting.RED),
									false);
							return 0;
						}

						long now = System.currentTimeMillis() / 1000L;
						long lastHtp = htpCooldowns.getOrDefault(source.getUuid(), 0L);
						if (now - lastHtp < RecallConfig.INSTANCE.htpCooldownSeconds) {
							long remaining = RecallConfig.INSTANCE.htpCooldownSeconds - (now - lastHtp);
							long h = remaining / 3600;
							long m = (remaining % 3600) / 60;
							long s = remaining % 60;
							
							StringBuilder timeStr = new StringBuilder();
							if (h > 0) timeStr.append(h).append(" giờ ");
							if (m > 0) timeStr.append(m).append(" phút ");
							if (s > 0 || timeStr.length() == 0) timeStr.append(s).append(" giây");
							
							source.sendMessage(
									Text.literal("§cBạn phải chờ " + timeStr.toString().trim() + " nữa để gửi yêu cầu HTP."),
									false);
							return 0;
						}
						htpCooldowns.put(source.getUuid(), now);

						tpaRequests.put(target.getUuid(), new TpaRequest(source.getUuid()));

						boolean hasEconomy = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("servershop");
						String costStr = (RecallConfig.INSTANCE.tpaCost > 0 && hasEconomy) ? String.valueOf(RecallConfig.INSTANCE.tpaCost) : "Miễn phí";

						source.sendMessage(
								Text.translatable(
										"message.recall_potion.htp.sent",
										target.getName().getString(),
										costStr)
										.formatted(Formatting.GREEN),
								false);

						Text acceptText = Text.translatable("message.recall_potion.htp.accept_btn")
								.styled(style -> style
										.withClickEvent(new ClickEvent.RunCommand("/htpaccept"))
										.withColor(Formatting.GREEN));

						Text denyText = Text.translatable("message.recall_potion.htp.deny_btn")
								.styled(style -> style
										.withClickEvent(new ClickEvent.RunCommand("/htpdeny"))
										.withColor(Formatting.RED));

						target.sendMessage(
								Text.translatable(
										"message.recall_potion.htp.receive",
										source.getName().getString(),
										costStr)
										.formatted(Formatting.YELLOW)
										.append(" ")
										.append(acceptText)
										.append(" ")
										.append(denyText),
								false);

						return 1;
					})));

			dispatcher.register(literal("htpaccept").executes(context -> {
				ServerPlayerEntity target = context.getSource().getPlayerOrThrow();

				TpaRequest request = tpaRequests.remove(target.getUuid());
				if (request == null || System.currentTimeMillis() - request.time > RecallConfig.INSTANCE.htpTimeoutSeconds * 1000L) {
					target.sendMessage(
							Text.translatable("message.recall_potion.htp.no_request")
									.formatted(Formatting.RED),
							false);
					return 0;
				}
				UUID sourceUuid = request.source;

				ServerPlayerEntity source = context.getSource()
						.getServer()
						.getPlayerManager()
						.getPlayer(sourceUuid);

				if (source == null) {
					target.sendMessage(
							Text.translatable("message.recall_potion.htp.offline")
									.formatted(Formatting.RED),
							false);
					return 0;
				}

				double cost = RecallConfig.INSTANCE.tpaCost;

				if (cost > 0 && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("servershop")) {
					try {
						Class<?> serverShopClass = Class.forName("systems.brn.servershop.ServerShop");
						Object balanceStorage = serverShopClass.getField("balanceStorage").get(null);

						if (balanceStorage == null) {
							throw new IllegalStateException("ServerShop balanceStorage is null");
						}

						java.lang.reflect.Method getBalance = balanceStorage
								.getClass()
								.getMethod("getBalance", UUID.class);

						java.lang.reflect.Method removeBalance = balanceStorage
								.getClass()
								.getMethod("removeBalance", UUID.class, double.class);

						double currentBalance = (double) getBalance.invoke(balanceStorage, source.getUuid());

						if (currentBalance < cost) {
							source.sendMessage(
									Text.translatable("message.recall_potion.htp.insufficient_funds_sender", cost)
											.formatted(Formatting.RED),
									false);
							target.sendMessage(
									Text.translatable("message.recall_potion.htp.insufficient_funds_target")
											.formatted(Formatting.RED),
									false);
							return 0;
						}

						removeBalance.invoke(balanceStorage, source.getUuid(), cost);

						source.sendMessage(
								Text.translatable("message.recall_potion.htp.paid", cost)
										.formatted(Formatting.GREEN),
								false);
					} catch (Exception e) {
						LOGGER.error("Failed to connect to ServerShop economy", e);
						source.sendMessage(
								Text.translatable("message.recall_potion.htp.economy_error")
										.formatted(Formatting.RED),
								false);
						return 0;
					}
				}

				TeleportTarget teleportTarget = new TeleportTarget(
						(ServerWorld) target.getWorld(),
						new Vec3d(target.getX(), target.getY(), target.getZ()),
						Vec3d.ZERO,
						target.getYaw(),
						target.getPitch(),
						TeleportTarget.NO_OP);

				// Play sound at original location before teleport
				((ServerWorld) source.getWorld()).playSound(
						null,
						source.getBlockPos(),
						SoundEvents.ENTITY_ENDERMAN_TELEPORT,
						SoundCategory.PLAYERS,
						1.0f,
						1.0f);

				source.teleportTo(teleportTarget);

				source.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0)); // 5 seconds
				
				((ServerWorld) target.getWorld()).playSound(
						null,
						target.getBlockPos(),
						SoundEvents.ENTITY_ENDERMAN_TELEPORT,
						SoundCategory.PLAYERS,
						1.0f,
						1.0f);
				
				((ServerWorld) target.getWorld()).spawnParticles(
						ParticleTypes.REVERSE_PORTAL,
						target.getX(),
						target.getY() + 1.0D,
						target.getZ(),
						50,
						0.5D,
						1.0D,
						0.5D,
						0.1D);

				source.sendMessage(
						Text.translatable(
								"message.recall_potion.htp.teleported_sender",
								target.getName().getString())
								.formatted(Formatting.GREEN),
						false);

				target.sendMessage(
						Text.translatable(
								"message.recall_potion.htp.teleported_target",
								source.getName().getString())
								.formatted(Formatting.GREEN),
						false);

				return 1;
			}));

			dispatcher.register(literal("htpdeny").executes(context -> {
				ServerPlayerEntity target = context.getSource().getPlayerOrThrow();

				TpaRequest request = tpaRequests.remove(target.getUuid());
				if (request == null || System.currentTimeMillis() - request.time > RecallConfig.INSTANCE.htpTimeoutSeconds * 1000L) {
					target.sendMessage(
							Text.translatable("message.recall_potion.htp.no_request")
									.formatted(Formatting.RED),
							false);
					return 0;
				}
				UUID sourceUuid = request.source;

				ServerPlayerEntity source = context.getSource()
						.getServer()
						.getPlayerManager()
						.getPlayer(sourceUuid);

				if (source != null) {
					source.sendMessage(
							Text.translatable(
									"message.recall_potion.htp.denied_sender",
									target.getName().getString())
									.formatted(Formatting.RED),
							false);
				}

				target.sendMessage(
						Text.translatable("message.recall_potion.htp.denied_target")
								.formatted(Formatting.GREEN),
						false);

				return 1;
			}));
		});

		LOGGER.info("Recall Potion Mod Initialized!");
	}
}