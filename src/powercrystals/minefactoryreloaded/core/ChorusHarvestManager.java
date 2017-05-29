package powercrystals.minefactoryreloaded.core;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChorusFlower;
import net.minecraft.block.BlockChorusPlant;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class ChorusHarvestManager implements IHarvestManager {

	private static final EnumFacing[] SEARCH_SIDES = new EnumFacing[] {EnumFacing.UP, EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH};

	private Stack<BlockPos> blocks = new Stack<>();
	private BlockPos origin;
	private World world;
	private boolean isDone = false;

	public ChorusHarvestManager(NBTBase tag) {

		if (tag instanceof NBTTagCompound)
			readFromNBT((NBTTagCompound) tag);

		if (origin == null) {
			origin = new BlockPos(0,0,0);
		}

	}

	public ChorusHarvestManager(World world, Area area) {

		reset(world, area, HarvestMode.HarvestTree, null);
	}

	@Override
	public void moveNext() {

		if (blocks.empty())
			isDone = true;
	}

	@Override
	public BlockPos getNextBlock() {

		if (blocks.empty()) {
			searchForChorusBlocks(origin);
		}

		return blocks.size() > 0 ? blocks.pop() : null;
	}

	private void searchForChorusBlocks(BlockPos pos) {

		blocks = new Stack<>();
		getBranchHarvestableBlocks(pos, blocks);
	}

	private boolean getBranchHarvestableBlocks(BlockPos currentPos, Stack<BlockPos> blocksToHarvest) {

		return getBranchHarvestableBlocks(currentPos, new ArrayList<>(), blocksToHarvest);
	}
	private boolean getBranchHarvestableBlocks(BlockPos currentPos, List<BlockPos> alreadySearched, Stack<BlockPos> blocksToHarvest) {

		boolean continueSearch;

		do {
			continueSearch = false;

			if (world.isBlockLoaded(currentPos)) {
				alreadySearched.add(currentPos);
				IBlockState state = world.getBlockState(currentPos);
				if (state.getBlock() == Blocks.CHORUS_FLOWER) {
					if (state.getValue(BlockChorusFlower.AGE) == 5) {
						blocksToHarvest.push(currentPos);
						return true;
					} else {
						blocksToHarvest.clear();
						return false;
					}
				}

				if (state.getBlock() == Blocks.CHORUS_PLANT) {
					blocksToHarvest.push(currentPos);

					List<EnumFacing> connectedSides = getConnectedSides(currentPos, alreadySearched);

					if (connectedSides.size() == 1) {
						continueSearch = true;
						currentPos = currentPos.offset(connectedSides.get(0));
					} else if (connectedSides.size() > 1) {
						//multiple branches attached
						Stack<BlockPos> branchBlocks = new Stack<>();
						boolean allBranchesHarvestable = getConnectedBranchesBlocks(currentPos, connectedSides, branchBlocks, alreadySearched);

						if (!allBranchesHarvestable)
							blocksToHarvest.clear();

						blocksToHarvest.addAll(branchBlocks);

						return allBranchesHarvestable;
					}
				}
			} else {
				blocksToHarvest.clear();

				return false;
			}
		} while (continueSearch);

		return false;
	}

	private boolean getConnectedBranchesBlocks(BlockPos currentPos, List<EnumFacing> connectedSides,
			Stack<BlockPos> branchBlocks, List<BlockPos> alreadySearched) {

		boolean allBranchesHarvestable = true;

		for (EnumFacing side : connectedSides) {
			Stack<BlockPos> harvestableBlocks = new Stack<>();
			allBranchesHarvestable &= getBranchHarvestableBlocks(currentPos.offset(side), alreadySearched, harvestableBlocks);
			branchBlocks.addAll(harvestableBlocks);
		}
		return allBranchesHarvestable;
	}

	private List<EnumFacing> getConnectedSides(BlockPos pos, List<BlockPos> alreadySearched) {

		List<EnumFacing> connectedSides = new ArrayList<>();
		for (EnumFacing facing : SEARCH_SIDES) {
			BlockPos currentPos = pos.offset(facing);
			if (!alreadySearched.contains(currentPos)) {
				if (plantIsConnectedOnSide(pos, facing)) {
					connectedSides.add(facing);
				}
			}
		}

		return connectedSides;
	}

	private boolean plantIsConnectedOnSide(BlockPos pos, EnumFacing side) {

		Block block  = world.getBlockState(pos.offset(side)).getBlock();
		return block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER;
	}

	@Override
	public BlockPos getOrigin() {

		return origin;
	}

	@Override
	public void reset(World world, Area area, HarvestMode harvestMode, Map<String, Boolean> settings) {

		setWorld(world);
		origin = area.getOrigin();
		isDone = false;
	}

	@Override
	public void setWorld(World world) {

		this.world = world;
	}

	@Override
	public boolean getIsDone() {

		return isDone;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {

		tag.setLong("origin", origin.toLong());

		int[] blockCoordinates = new int[blocks.size() * 3];

		for (int i = 0; i < blocks.size(); i++) {
			int arrayIndex = i * 3;
			BlockPos currentPos = blocks.get(i);
			blockCoordinates[arrayIndex] = currentPos.getX();
			blockCoordinates[arrayIndex + 1] = currentPos.getX();
			blockCoordinates[arrayIndex + 2] = currentPos.getX();
		}

		tag.setIntArray("blocks", blockCoordinates);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {

		origin = BlockPos.fromLong(tag.getLong("origin"));

		int[] blockCoordinates = tag.getIntArray("blocks");

		blocks = new Stack<>();
		for (int i = 0; i < blockCoordinates.length; i += 3) {
			BlockPos pos = new BlockPos(blockCoordinates[i], blockCoordinates[i + 1], blockCoordinates[i + 2]);
			blocks.push(pos);
		}
	}

	@Override
	public void free() {

		blocks.clear();
	}
}
