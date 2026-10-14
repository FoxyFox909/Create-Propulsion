package com.deltasf.createpropulsion.telegraph;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllTags.AllItemTags;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Scroll value behaviour for the telegraph: hold right-click anywhere on the
 * block to open Create's value settings board and slide across 0-15 signal
 * strength.
 *
 * The behaviour's internal value runs at SUB_STEPS notches per redstone level.
 * Create's settings board draws a fixed 2px per notch with a milestone gap
 * every interval, so extra notches widen the slider: with SUB_STEPS = 2 and a
 * milestone separator after every level, each redstone level gets roughly
 * twice the pixels, plus a visible bucket per level. Raise SUB_STEPS to make
 * the board even wider.
 */
public class TelegraphScrollBehaviour extends ScrollValueBehaviour {
    public static final int SUB_STEPS = 2;
    public static final int MAX_INTERNAL = 15 * SUB_STEPS;

    public TelegraphScrollBehaviour(SmartBlockEntity be) {
        super(Component.translatable("createpropulsion.gui.telegraph.power"), be, new TelegraphValueBox());
        between(0, MAX_INTERNAL);
        //The hover value box should display the redstone level, not internal notches
        withFormatter(v -> String.valueOf(toRedstone(v)));
    }

    public static int toRedstone(int internalValue) {
        return Mth.clamp(internalValue / SUB_STEPS, 0, 15);
    }

    public static int toInternal(int redstoneLevel) {
        return Mth.clamp(redstoneLevel, 0, 15) * SUB_STEPS;
    }

    /** The whole block is the interaction target, not just the floating value box. */
    @Override
    public boolean testHit(Vec3 hit) {
        return true;
    }

    /** Let wrenches through so the block can still be rotated and dismantled. */
    @Override
    public boolean bypassesInput(ItemStack mainhandItem) {
        return AllItemTags.WRENCH.matches(mainhandItem);
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        ImmutableList<Component> row = ImmutableList.of(
                Component.translatable("createpropulsion.gui.telegraph.power_row"));
        return new ValueSettingsBoard(label, MAX_INTERNAL, SUB_STEPS, row,
                new ValueSettingsFormatter(this::formatValue));
    }

    @Override
    public void setValueSettings(Player player, ValueSettings valueSetting, boolean ctrlHeld) {
        int value = Mth.clamp(valueSetting.value(), 0, MAX_INTERNAL);
        if (!valueSetting.equals(getValueSettings()))
            playFeedbackSound(this);
        setValue(value);
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, value);
    }

    public MutableComponent formatValue(ValueSettings settings) {
        return CreateLang.number(toRedstone(settings.value())).component();
    }
}
