package com.aizistral.enigmaticlegacy.items;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.aizistral.enigmaticlegacy.api.generic.SubscribeConfig;
import com.aizistral.enigmaticlegacy.handlers.SuperpositionHandler;
import com.aizistral.enigmaticlegacy.helpers.ItemLoreHelper;
import com.aizistral.enigmaticlegacy.items.generic.ItemBasePotion;
import com.aizistral.omniconfig.wrappers.Omniconfig;
import com.aizistral.omniconfig.wrappers.OmniconfigWrapper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Consumable that permanently grants one additional Spellstone slot, similar
 * to how Bottle of Ichor grants an additional Charm slot.
 */
public class SpellstoneElixir extends ItemBasePotion {
	public static final String CONSUMED_KEY = "SpellstoneElixirsConsumed";
	public static Omniconfig.IntParameter maxUses;

	@SubscribeConfig
	public static void handleConfig(OmniconfigWrapper builder) {
		builder.pushPrefix("SpellstoneElixir");

		maxUses = builder
				.comment("How many Spellstone Elixirs a single player can consume in total. Each one grants one additional Spellstone slot.")
				.min(1)
				.max(10)
				.getInt("MaxUses", 1);

		builder.popPrefix();
	}

	public SpellstoneElixir() {
		super(getDefaultProperties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
	}

	/** Deterministic modifier ID for the n-th elixir, so slots can be re-applied safely. */
	private static UUID slotModifierId(int index) {
		return UUID.nameUUIDFromBytes(("enigmaticlegacy:spellstone_elixir_" + index).getBytes(StandardCharsets.UTF_8));
	}

	/** (Re)applies all extra Spellstone slots this player has earned. Safe to call repeatedly. */
	public static void restoreSlots(Player player) {
		int consumed = SuperpositionHandler.getPersistentInteger(player, CONSUMED_KEY, 0);

		if (consumed <= 0)
			return;

		CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> handler.getStacksHandler("spellstone").ifPresent(stacks -> {
			for (int i = 0; i < consumed; i++) {
				UUID id = slotModifierId(i);

				if (!stacks.getModifiers().containsKey(id)) {
					stacks.addPermanentModifier(new AttributeModifier(id, "SpellstoneElixirSlot", 1, Operation.ADDITION));
				}
			}
		}));
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void appendHoverText(ItemStack stack, @Nullable Level worldIn, List<Component> list, TooltipFlag flagIn) {
		if (Screen.hasShiftDown()) {
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.spellstoneElixir1");
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.spellstoneElixir2", ChatFormatting.GOLD, maxUses.getValue());
		} else {
			ItemLoreHelper.addLocalizedString(list, "tooltip.enigmaticlegacy.holdShift");
		}
	}

	@Override
	public boolean isFoil(ItemStack pStack) {
		return true;
	}

	@Override
	public boolean canDrink(Level world, Player player, ItemStack potion) {
		return SuperpositionHandler.getPersistentInteger(player, CONSUMED_KEY, 0) < maxUses.getValue();
	}

	@Override
	public void onConsumed(Level worldIn, Player player, ItemStack potion) {
		if (player instanceof ServerPlayer playerMP) {
			int consumed = SuperpositionHandler.getPersistentInteger(playerMP, CONSUMED_KEY, 0);

			if (consumed >= maxUses.getValue())
				return;

			SuperpositionHandler.setPersistentInteger(playerMP, CONSUMED_KEY, consumed + 1);
			restoreSlots(playerMP);

			playerMP.level().playSound(null, playerMP.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
			playerMP.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1, false, true));
			playerMP.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 2, false, true));
		}
	}

}
