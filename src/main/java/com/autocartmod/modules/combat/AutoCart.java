package com.autocartmod.modules.combat;

import com.autocartmod.modules.Module;
import com.autocartmod.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.List;

public class AutoCart extends Module {

    public final IntSetting explodeDelay = new IntSetting(
            "Explode Delay", "Ticks sau khi đặt cart thì đánh nổ", 3, 0, 40);

    public final IntSetting placeDelay = new IntSetting(
            "Place Delay", "Ticks chờ giữa mỗi lần đặt", 5, 0, 60);

    public final IntSetting hotbarSlot = new IntSetting(
            "Hotbar Slot", "Slot hotbar chứa cart (1-9)", 1, 1, 9);

    public final BooleanSetting autoRefill = new BooleanSetting(
            "Auto Refill", "Tự lấy TNT Cart từ kho vào hotbar", true);

    private int ticksSincePlace = 999;
    private boolean waitingToExplode = false;

    public AutoCart() {
        super("AutoCart", "Đặt TNT Minecart và tự kích nổ.", Category.COMBAT);
        settings.add(explodeDelay);
        settings.add(placeDelay);
        settings.add(hotbarSlot);
        settings.add(autoRefill);
    }

    @Override
    public void onEnable() {
        ticksSincePlace = 999;
        waitingToExplode = false;
    }

    @Override
    public void onDisable() {
        waitingToExplode = false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (fullNullCheck()) return;

        ticksSincePlace++;

        int slot = hotbarSlot.getValue() - 1;

        // Auto refill: tìm TNT cart trong toàn bộ inventory, pick vào hotbar slot
        if (autoRefill.getValue()) {
            ItemStack inSlot = mc.player.getInventory().getStack(slot);
            if (inSlot.getItem() != Items.TNT_MINECART) {
                // Tìm trong inventory (bao gồm cả hotbar)
                for (int i = 0; i < mc.player.getInventory().size(); i++) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (stack.getItem() == Items.TNT_MINECART) {
                        // Dùng packet pick-from-inventory để swap vào hotbar slot hiện tại
                        mc.player.getInventory().selectedSlot = slot;
                        mc.getNetworkHandler().sendPacket(new PickFromInventoryC2SPacket(i));
                        break;
                    }
                }
            }
        }

        // Kiểm tra có cart trong slot không
        ItemStack held = mc.player.getInventory().getStack(slot);
        if (held.getItem() != Items.TNT_MINECART) return;

        // Chuyển sang slot cart
        mc.player.getInventory().selectedSlot = slot;

        // Phase 1: Đặt cart
        if (!waitingToExplode && ticksSincePlace >= placeDelay.getValue()) {
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                ticksSincePlace = 0;
                waitingToExplode = true;
            }
        }

        // Phase 2: Đánh nổ sau delay
        if (waitingToExplode && ticksSincePlace >= explodeDelay.getValue()) {
            waitingToExplode = false;

            List<TntMinecartEntity> carts = mc.world.getEntitiesByClass(
                    TntMinecartEntity.class,
                    mc.player.getBoundingBox().expand(8),
                    e -> true
            );

            if (!carts.isEmpty()) {
                TntMinecartEntity cart = carts.get(0);
                mc.interactionManager.attackEntity(mc.player, cart);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    @Override
    public String getDisplayInfo() {
        return waitingToExplode ? "§cKích nổ..." : "§aSẵn sàng";
    }
}
