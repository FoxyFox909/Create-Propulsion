package com.deltasf.createpropulsion.ponder;

import com.deltasf.createpropulsion.registries.PropulsionBlocks;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.ParticleEmitter;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ThrusterScenes {
    public static void single(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("thruster_single", "Single Thrusters");
        scene.configureBasePlate(0, 0, 6);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);

        Selection lowerCog = util.select().position(5, 0, 4);
        scene.world().setKineticSpeed(lowerCog, 64);

        Selection upperCogs = util.select().fromTo(5, 1, 4, 1, 1, 4);
        scene.world().setKineticSpeed(upperCogs, -64);

        Selection pump = util.select().position(1, 1, 3);
        scene.world().setKineticSpeed(pump, 64);

//        scene.world().propagatePipeChange(pump.po);

        scene.overlay().showText(80)
                .sharedText("single_thruster.intro")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 1)));
        scene.idle(80);
        scene.addKeyframe();

        Selection lever = util.select().position(1, 1, 1);
        scene.world().modifyBlockEntityNBT(lever, AnalogLeverBlockEntity.class, nbt -> {
            nbt.putInt("State", 7);
        });
        scene.overlay().showText(80)
                .sharedText("single_thruster.redstone_power_level")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 1)));
        /* Thruster will cause a crash trying to emit particles during ponder lmao */
//        Selection thruster = util.select().position(2, 1, 1);
//        scene.world().modifyBlockEntityNBT(thruster, ThrusterBlockEntity.class, nbt -> {
//            nbt.putInt("RedstoneInput", 7);
//        });
        scene.idle(80);
        scene.addKeyframe();

        Vec3 center = util.vector().centerOf(util.grid().at(2, 1, 0));

        BlockParticleOption obsidianParticle =
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());

        ParticleEmitter emitter = (level, x,y, z) -> {
            double ox = x + (level.random.nextDouble() - 0.5);
            double oy = y + (level.random.nextDouble() - 0.5);
            double oz = z + (level.random.nextDouble() - 0.5);
            level.addParticle(obsidianParticle, ox, oy, oz, 0, 0, 0);
        };
        scene.effects().emitParticles(center, emitter, 64f, 1);

        scene.world().setBlock(util.grid().at(2, 1, 0), Blocks.OBSIDIAN.defaultBlockState(), true);
        scene.overlay().showText(80)
                .sharedText("single_thruster.about_obstruction")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 1)));
        scene.idle(80);
    }


    public static void multiblock(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("thruster_multiblock", "Multiblock Thrusters");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        scene.overlay().showText(80)
                .sharedText("multiblock_thruster.intro")
                .placeNearTarget()
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 1)));

        BlockState thrusterState = PropulsionBlocks.THRUSTER_BLOCK.getDefaultState().setValue(
                DirectionalBlock.FACING, Direction.WEST);


        scene.world().setBlock(util.grid().at(2, 1, 1), thrusterState, false);
        scene.world().showSection(util.select().position(3, 1,1), Direction.DOWN);
        scene.idle(5);
        scene.world().setBlock(util.grid().at(2, 1, 2), thrusterState, false);
        scene.world().showSection(util.select().position(3, 1,2), Direction.DOWN);
        scene.idle(5);
        scene.world().setBlock(util.grid().at(3, 1, 1), thrusterState, false);
        scene.world().showSection(util.select().position(4, 1,1), Direction.DOWN);
        scene.idle(5);
        scene.world().setBlock(util.grid().at(3, 1, 2), thrusterState, false);
        scene.world().showSection(util.select().position(4, 1,2), Direction.DOWN);

        scene.world().setBlock(util.grid().at(2, 2, 1), thrusterState, false);
        scene.world().showSection(util.select().position(3, 2,1), Direction.DOWN);
        scene.idle(5);
        scene.world().setBlock(util.grid().at(2, 2, 2), thrusterState, false);
        scene.world().showSection(util.select().position(3, 2,2), Direction.DOWN);
        scene.idle(5);
        scene.world().setBlock(util.grid().at(3, 2, 1), thrusterState, false);
        scene.world().showSection(util.select().position(4, 2,1), Direction.DOWN);
        scene.idle(5);
        scene.world().setBlock(util.grid().at(3, 2, 2), thrusterState, false);
        scene.world().showSection(util.select().position(4, 2,2), Direction.DOWN);

        scene.idle(40);

        scene.world().setBlock(util.grid().at(2, 1, 1), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 1), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 1, 2), Blocks.AIR.defaultBlockState(), false);

        scene.world().setBlock(util.grid().at(2, 2, 1), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(2, 2, 2), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 2, 1), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(util.grid().at(3, 2, 2), Blocks.AIR.defaultBlockState(), false);

        //Transition to multiblock
        Selection multiThruster = util.select().fromTo(4, 1, 1, 5, 2, 2);
        ElementLink<WorldSectionElement> multiSection = scene.world().showIndependentSection(multiThruster,
                Direction.DOWN);
//        scene.world().showSectionAndMerge(multiThruster, Direction.DOWN, multiSection);
        scene.world().moveSection(
                multiSection,
                util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.UP), 0);
//        scene.world();

//        // Controller
//        scene.idle(5);
//        scene.world().modifyBlockEntityNBT(util.select().position(3, 1, 1),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(3, 1, 2),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", 0);
//                    nbt.putInt("ControllerOffY", 0);
//                    nbt.putInt("ControllerOffZ", -1);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(4, 1, 1),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", -1);
//                    nbt.putInt("ControllerOffY", 0);
//                    nbt.putInt("ControllerOffZ", 0);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(4, 1, 2),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", -1);
//                    nbt.putInt("ControllerOffY", 0);
//                    nbt.putInt("ControllerOffZ", -1);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(3, 2, 1),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", 0);
//                    nbt.putInt("ControllerOffY", -1);
//                    nbt.putInt("ControllerOffZ", 0);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(3, 2, 2),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", 0);
//                    nbt.putInt("ControllerOffY", -1);
//                    nbt.putInt("ControllerOffZ", -1);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(4, 2, 1),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", -1);
//                    nbt.putInt("ControllerOffY", -1);
//                    nbt.putInt("ControllerOffZ", 0);
//                });
//        scene.world().modifyBlockEntityNBT(util.select().position(4, 2, 2),
//                ThrusterBlockEntity.class,
//                nbt -> {
//                    nbt.putInt("Width", 2);
//                    nbt.putInt("ControllerOffX", -1);
//                    nbt.putInt("ControllerOffY", -1);
//                    nbt.putInt("ControllerOffZ", -1);
//                });

        scene.idle(80);


    }
}

