package dev.transport.truck;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder(TransportTruck.ID)
@PrefixGameTestTemplate(false)
public final class TruckGameTests {
  @GameTest(template = "test_lane", timeoutTicks = 230)
  public static void steppedCourse(GameTestHelper h) {
    for (int x = 0; x < 16; x++)
      for (int z = 0; z < 48; z++) {
        h.setBlock(x, 0, z, Blocks.STONE);
        if (z >= 13) h.setBlock(x, 1, z, Blocks.STONE);
        if (z == 35) {
          h.setBlock(x, 2, z, Blocks.STONE);
          h.setBlock(x, 3, z, Blocks.STONE);
        }
      }
    var level = h.getLevel();
    TruckEntity t = TransportTruck.TYPE.get().create(level);
    Vec3 origin = h.absoluteVec(new Vec3(8.5, 1, 6));
    t.moveTo(origin.x, origin.y, origin.z, 0, 0);
    h.assertTrue(level.addFreshEntity(t), "spawn failed");
    ServerPlayer driver = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
    driver.setGameMode(GameType.CREATIVE);
    t.interact(driver, InteractionHand.MAIN_HAND);
    h.assertTrue(t.getControllingPassenger() == driver, "driver seat");
    var passenger = h.makeMockPlayer();
    h.assertTrue(passenger.startRiding(t), "second seat");
    t.cargo.setItem(53, new ItemStack(Items.RAW_IRON, 29));
    var save = new CompoundTag();
    t.addAdditionalSaveData(save);
    var copy = TransportTruck.TYPE.get().create(level);
    copy.readAdditionalSaveData(save);
    h.assertTrue(copy.cargo.getItem(53).getCount() == 29, "cargo persistence");
    double[] stop = {0};
    boolean[] result = new boolean[4];
    h.onEachTick(
        () -> {
          long tick = h.getTick();
          if (tick < 98) {
            t.controls(driver, 1, 0);
            result[0] |= t.getY() > origin.y + .9;
          } else if (tick == 98) {
            h.assertTrue(result[0], "one-block climb");
            stop[0] = t.getZ();
            h.assertTrue(stop[0] > origin.z + 22 && stop[0] < origin.z + 29, "two-block wall");
          } else if (tick < 126) {
            t.controls(driver, 2, 0);
            result[1] |= t.speed < -1 && t.getZ() < stop[0] - 2;
          } else if (tick < 145) {
            t.controls(driver, 6, 0);
            result[2] |= t.steer > .35;
          } else if (tick < 170) {
            t.controls(driver, 10, 0);
            result[3] |= t.steer < -.35;
          } else if (tick == 170) {
            h.assertTrue(
                result[1] && result[2] && result[3], "reverse and both steering directions");
            passenger.stopRiding();
            driver.stopRiding();
            h.assertTrue(t.getPassengers().isEmpty(), "dismount");
            h.succeed();
          }
        });
  }
}
