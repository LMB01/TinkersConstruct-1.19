package slimeknights.tconstruct.tools.modifiers.ability.sling;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.TinkerHooks;
import slimeknights.tconstruct.library.modifiers.hook.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.modifiers.util.ModifierHookMap.Builder;
import slimeknights.tconstruct.library.tools.capability.TinkerDataCapability;
import slimeknights.tconstruct.library.tools.capability.TinkerDataKeys;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.tools.TinkerModifiers;

import static slimeknights.tconstruct.tools.modifiers.upgrades.ranged.ScopeModifier.SCOPE;

/**
 * Shared logic for all slinging modifiers
 */
public abstract class SlingModifier extends NoLevelsModifier implements GeneralInteractionModifierHook {
  @Override
  protected void registerHooks(Builder builder) {
    builder.addHook(this, TinkerHooks.CHARGEABLE_INTERACT);
  }

  @Override
  public InteractionResult onToolUse(IToolStackView tool, ModifierEntry modifier, Player player, InteractionHand hand, InteractionSource source) {
    if (!tool.isBroken() && source == InteractionSource.RIGHT_CLICK) {
      ModifierUtil.startUsingItemWithDrawtime(tool, modifier.getId(), player, hand, 1.5f);
    }
    return InteractionResult.SUCCESS;
  }

  @Override
  public void onUsingTick(IToolStackView tool, ModifierEntry modifier, LivingEntity entity, int timeLeft) {
    if (entity.level.isClientSide && tool.getModifierLevel(TinkerModifiers.scope.getId()) > 0) {
      float drawTime = tool.getPersistentData().getInt(ModifiableLauncherItem.KEY_DRAWTIME);
      float chargeTime = this.getUseDuration(tool, modifier) - timeLeft;
      if (chargeTime > 0 && drawTime > 0) {
        entity.getCapability(TinkerDataCapability.CAPABILITY).ifPresent(data ->
          data.computeIfAbsent(TinkerDataKeys.FOV_MODIFIER).set(SCOPE, 1 - (0.6f * Math.min(chargeTime / drawTime, 1))));
      }
    }
  }

  @Override
  public boolean onStoppedUsing(IToolStackView tool, ModifierEntry modifier, LivingEntity entity, int timeLeft) {
    if (entity.level.isClientSide) {
      entity.getCapability(TinkerDataCapability.CAPABILITY).ifPresent(data -> data.computeIfAbsent(TinkerDataKeys.FOV_MODIFIER).remove(SCOPE));
    }
    return false;
  }

  @Override
  public int getUseDuration(IToolStackView tool, ModifierEntry modifier) {
    return 72000;
  }

  @Override
  public UseAnim getUseAction(IToolStackView tool, ModifierEntry modifier) {
    return UseAnim.BOW;
  }

  /** Gets the force to launch the sling at, considers drawspeed and velocity */
  protected float getForce(IToolStackView tool, ModifierEntry entry, LivingEntity living, int timeLeft, boolean applyKnockback) {
    int chargeTime = getUseDuration(tool, entry) - timeLeft;
    if (chargeTime < 0) {
      return 0;
    }
    // if using knockback, boost projectile damage by 0.5 per level, that is a 25% boost, same as power
    // TODO: this is pretty hardcoded, is there a good way to softcode this?
    float knockback = 0;
    if (applyKnockback) {
      knockback = tool.getModifierLevel(TinkerModifiers.knockback.getId()) / 2f;
    }
    float force = ModifierUtil.getToolCharge(tool, chargeTime)
                  * (ConditionalStatModifierHook.getModifiedStat(tool, living, ToolStats.PROJECTILE_DAMAGE) + knockback) / 2f
                  * ConditionalStatModifierHook.getModifiedStat(tool, living, ToolStats.VELOCITY);
    // knockback also means we should apply padded, divide per level
    if (applyKnockback) {
      force /= Math.pow(2, tool.getModifierLevel(TinkerModifiers.padded.getId()));
    }
    return force;
  }
}
