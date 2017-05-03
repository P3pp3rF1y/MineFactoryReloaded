package powercrystals.minefactoryreloaded.tile.transport.refactor;

import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.vec.Vector3;
import cofh.core.render.hitbox.CustomHitBox;
import cofh.core.render.hitbox.ICustomHitBox;
import cofh.core.util.CoreUtils;
import cofh.lib.util.helpers.FluidHelper;
import cofh.lib.util.helpers.StringHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import powercrystals.minefactoryreloaded.core.IGridController;
import powercrystals.minefactoryreloaded.core.INode;
import powercrystals.minefactoryreloaded.core.ITraceable;
import powercrystals.minefactoryreloaded.core.MFRUtil;
import powercrystals.minefactoryreloaded.net.Packets;
import powercrystals.minefactoryreloaded.tile.base.TileEntityBase;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static powercrystals.minefactoryreloaded.block.transport.BlockRedNetCable.subSelection;
import static powercrystals.minefactoryreloaded.tile.transport.refactor.FluidNetwork.TRANSFER_RATE;

public class TileEntityPlasticPipe extends TileEntityBase implements INode, ITraceable, ICustomHitBox
{

	private byte[] sideMode = { 1, 1, 1, 1, 1, 1, 0 };
	private IFluidHandler[] handlerCache = null;
	private PlasticPipeUpgrade upgrade = PlasticPipeUpgrade.NONE;
	private boolean deadCache = true;

	private boolean readFromNBT = false;

	private boolean isPowered = false;
	boolean isNode = false;
	FluidStack fluidForGrid = null;

	FluidNetwork _grid;

	public TileEntityPlasticPipe() {

	}

	@Override
	// cannot share mcp names
	public boolean isNotValid() {

		return tileEntityInvalid;
	}

	@Override
	public void invalidate() {

		if (_grid != null) {
			removeFromGrid();
		}
		super.invalidate();
	}

	private void removeFromGrid() {

		_grid.removeConduit(this);
		markForRegen();
		deadCache = true;
		_grid = null;
	}

	private void markForRegen() {

		int c = 0;
		for (int i = 6; i-- > 0;)
			if (sideMode[i] == ((2 << 2) | 1))
				++c;
		if (c > 1)
			_grid.regenerate();
	}

	boolean firstTick = true;

	@Override
	public void update() {

		//TODO remove in favor of ASM
		if (firstTick) {
			cofh_validate();
			firstTick = false;
		}

		//TODO yet again needs a non tickable base TE
	}

	@Override
	public void onNeighborBlockChange() {

		boolean last = isPowered;
		isPowered = upgrade.getPowered(CoreUtils.isRedstonePowered(this));
		if (last != isPowered)
			MFRUtil.notifyBlockUpdate(worldObj, pos);
	}

	private void reCache() {

		if (deadCache) {
			for (EnumFacing dir : EnumFacing.VALUES)
				if (worldObj.isBlockLoaded(pos.offset(dir)))
					addCache(MFRUtil.getTile(worldObj, pos.offset(dir)));
			deadCache = false;
			FluidNetwork.HANDLER.addConduitForUpdate(this);
		}
	}

	public void onMerge() {

		markChunkDirty();
		notifyNeighborTileChange();
		deadCache = true;
		reCache();
		MFRUtil.notifyBlockUpdate(worldObj, pos);
	}

	@Override
	public void cofh_validate() {

		super.cofh_validate();
		deadCache = true;
		handlerCache = null;
		if (worldObj.isRemote) return;
		if (_grid == null) {
			incorporateTiles();
			if (_grid == null) {
				setGrid(new FluidNetwork(this));
			}
		}
		readFromNBT = true;
		reCache();
		markDirty();
		MFRUtil.notifyBlockUpdate(worldObj, pos);
	}

	@Override
	public void onNeighborTileChange(BlockPos neighborPos) {

		if (worldObj.isRemote | deadCache)
			return;
		TileEntity tile = worldObj.isBlockLoaded(neighborPos) ? worldObj.getTileEntity(neighborPos) : null;

		Vec3i diff = pos.subtract(neighborPos);
		addCache(tile, EnumFacing.getFacingFromVector(diff.getX(), diff.getY(), diff.getZ()));
	}

	private void addCache(TileEntity tile) {

		if (tile == null) return;
		Vec3i diff = pos.subtract(tile.getPos());
		addCache(tile, EnumFacing.getFacingFromVector(diff.getX(), diff.getY(), diff.getZ()));
	}

	private void addCache(TileEntity tile, EnumFacing side) {

		if (handlerCache != null)
			handlerCache[side.ordinal()] = null;
		int lastMode = sideMode[side.ordinal()];
		sideMode[side.ordinal()] &= 3;
		if (tile instanceof TileEntityPlasticPipe) {
			TileEntityPlasticPipe cable = (TileEntityPlasticPipe) tile;
			sideMode[side.ordinal()] &= ~2;
			sideMode[side.ordinal()] |= (2 << 2);
			if (cable.isInterfacing(side)) {
				if (_grid == null && cable._grid != null) {
					cable._grid.addConduit(this);
				}
				if (cable._grid == _grid) {
					sideMode[side.ordinal()] |= 1; // always enable
				}
			} else {
				sideMode[side.ordinal()] &= ~3;
			}
		} else if (tile != null && tile.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side)) {
			//if (((IFluidHandler)tile).canFill(EnumFacing.VALID_DIRECTIONS[side]))
			{
				if (handlerCache == null) handlerCache = new IFluidHandler[6];
				handlerCache[side.ordinal()] = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
				sideMode[side.ordinal()] |= 1 << 2;
			}
		}
		if (!deadCache) {
			FluidNetwork.HANDLER.addConduitForUpdate(this);
			if (lastMode != sideMode[side.ordinal()])
				MFRUtil.notifyBlockUpdate(worldObj, pos);
		}
	}

	private void incorporateTiles() {

		if (_grid == null) {
			boolean hasGrid = false;
			for (EnumFacing dir : EnumFacing.VALUES) {
				if (readFromNBT && (sideMode[dir.getOpposite().ordinal()] & 1) == 0) continue;
				if (worldObj.isBlockLoaded(pos.offset(dir))) {
					TileEntityPlasticPipe pipe = MFRUtil.getTile(worldObj, pos.offset(dir),TileEntityPlasticPipe.class);
					if (pipe != null) {
						if (pipe._grid != null &&
								(readFromNBT ? pipe.couldInterface(this) : pipe.canInterface(this))) {
							if (hasGrid) {
								pipe._grid.mergeGrid(_grid);
							} else {
								if (pipe._grid.addConduit(this)) {
									hasGrid = true;
									mergeWith(pipe, dir.ordinal());
								}
							}
						}
					}
				}
			}
		}
	}

	public boolean canInterface(TileEntityPlasticPipe te, EnumFacing dir) {

		if ((sideMode[dir.ordinal()] & 1) == 0) return false;
		return canInterface(te);
	}

	public boolean canInterface(TileEntityPlasticPipe te) {

		//new pipe not connected to anything yet - thus can interface with
		if (_grid != null && te._grid == null) {
			return true;
		}

		if (_grid != null && te._grid != null)
			if (_grid.storage.getFluid() == te._grid.storage.getFluid())
				return true;
			else
				return FluidHelper.isFluidEqualOrNull(_grid.storage.getFluid(), te._grid.storage.getFluid());
		return fluidForGrid == te.fluidForGrid || FluidHelper.isFluidEqual(fluidForGrid, te.fluidForGrid);
	}

	public boolean couldInterface(TileEntityPlasticPipe te) {

		if (_grid != null && te._grid != null)
			if (_grid.storage.getFluid() == te._grid.storage.getFluid())
				return true;
			else
				return FluidHelper.isFluidEqualOrNull(_grid.storage.getFluid(), te._grid.storage.getFluid());
		return fluidForGrid == te.fluidForGrid || FluidHelper.isFluidEqualOrNull(fluidForGrid, te.fluidForGrid);
	}

	private void mergeWith(TileEntityPlasticPipe te, int side) {

		if (_grid != null && te._grid != null && couldInterface(te)) {
			te._grid.mergeGrid(_grid);
			final byte one = 1;
			setMode(side, one);
			te.setMode(side ^ 1, one);
		}
	}

	@Override
	protected NBTTagCompound writePacketData(NBTTagCompound tag) {

		tag.setInteger("mode[0]", (sideMode[0] & 0xFF) | ((sideMode[1] & 0xFF) << 8) | ((sideMode[2] & 0xFF) << 16) |
				((sideMode[3] & 0xFF) << 24));
		tag.setInteger("mode[1]", (sideMode[4] & 0xFF) | ((sideMode[5] & 0xFF) << 8) | ((sideMode[6] & 0xFF) << 16) |
				(isPowered ? 1 << 24 : 0));
		tag.setByte("upgrade", (byte) upgrade.ordinal());

		return super.writePacketData(tag);
	}

	@Override
	public SPacketUpdateTileEntity getUpdatePacket() {

		if (deadCache)
			return null;

		return super.getUpdatePacket();
	}

	@Override
	protected void handlePacketData(NBTTagCompound tag) {

		super.handlePacketData(tag);

		int mode = tag.getInteger("mode[0]");
		sideMode[0] = (byte) ((mode >> 0) & 0xFF);
		sideMode[1] = (byte) ((mode >> 8) & 0xFF);
		sideMode[2] = (byte) ((mode >> 16) & 0xFF);
		sideMode[3] = (byte) ((mode >> 24) & 0xFF);
		mode = tag.getInteger("mode[1]");
		sideMode[4] = (byte) ((mode >> 0) & 0xFF);
		sideMode[5] = (byte) ((mode >> 8) & 0xFF);
		sideMode[6] = (byte) ((mode >> 16) & 0xFF);
		isPowered = (mode >> 24) > 0;
		upgrade = PlasticPipeUpgrade.values()[tag.getByte("upgrade")];
	}

	public void setUpgrade(PlasticPipeUpgrade upgrade) {

		this.upgrade = upgrade;
	}

	public PlasticPipeUpgrade getUpgrade() {

		return upgrade;
	}

	@SideOnly(Side.CLIENT)
	public void setModes(byte[] modes) {

		sideMode = modes;
	}

	public byte getMode(int subSide) {

		return (byte) (sideMode[subSide < 6 ? EnumFacing.VALUES[subSide].getOpposite().ordinal() : 6] & 3);
	}

	public void setMode(int subSide, byte mode) {

		subSide = subSide < 6 ? subSide ^ 1 : 6;
		mode &= 3;
		int t = sideMode[subSide];
		boolean mustUpdate = (mode != (t & 3));
		sideMode[subSide] = (byte) ((t & ~3) | mode);
		if (mustUpdate)
		{
			FluidNetwork.HANDLER.addConduitForUpdate(this);
		}
	}

	// internal

	public boolean isInterfacing(EnumFacing to) {

		int bSide = to.ordinal() ^ 1;
		int mode = sideMode[bSide] >> 2;
		return ((sideMode[bSide] & 1) != 0) & (sideMode[6] == 1 ? mode == 2 : mode != 0);
	}

	public int interfaceMode(EnumFacing to) {

		int bSide = to.ordinal() ^ 1;
		int mode = sideMode[bSide] >> 2;
		return (sideMode[bSide] & 1) != 0 ? mode : 0;
	}

	public boolean isPowered() {

		return isPowered;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {

		super.readFromNBT(nbt);
		readFromNBT = true;
		upgrade = PlasticPipeUpgrade.values()[nbt.getByte("Upgrade")];
		isPowered = nbt.getBoolean("Power");
		sideMode = nbt.getByteArray("SideMode");
		if (sideMode.length != 7)
			sideMode = new byte[] { 1, 1, 1, 1, 1, 1, 0 };
		if (nbt.hasKey("Fluid"))
			fluidForGrid = FluidStack.loadFluidStackFromNBT(nbt.getCompoundTag("Fluid"));
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound nbt) {

		super.writeToNBT(nbt);
		nbt.setByte("Upgrade", (byte) upgrade.ordinal());
		nbt.setBoolean("Power", isPowered);
		nbt.setByteArray("SideMode", sideMode);
		if (_grid != null) {
			if (isNode) {
				fluidForGrid = _grid.storage.drain(_grid.getNodeShare(this), false);
			} else {
				fluidForGrid = _grid.storage.drain(0, false);
			}
			if (fluidForGrid != null)
				nbt.setTag("Fluid", fluidForGrid.writeToNBT(new NBTTagCompound()));
		} else if (fluidForGrid != null)
			nbt.setTag("Fluid", fluidForGrid.writeToNBT(new NBTTagCompound()));

		return nbt;
	}

	void extract(EnumFacing side, IFluidTank tank) {

		if (deadCache) return;
		int bSide = side.ordinal();
		int m = sideMode[bSide];
		if (isPowered & ((m & 1) != 0) & (m & 2) == 2) {
			switch (m >> 2) {
			case 1: // IFluidHandler
				if (handlerCache != null) {
					IFluidHandler fluidHandler = handlerCache[bSide];
					FluidStack e = fluidHandler.drain(TRANSFER_RATE, false);
					if (e != null && e.amount > 0)
					{
						fluidHandler.drain(tank.fill(e, true), true);
					}
				}
				break;
			case 2: // TileEntityPlasticPipe
				break;
			case 0: // no mode
				// no-op
				break;
			}
		}
	}

	int transfer(EnumFacing side, FluidStack fluid, Fluid f) {

		if (deadCache) return 0;
		int bSide = side.ordinal();
		int m = sideMode[bSide];
		if (((m & 1) != 0) & (m & 2) == 0) {
			switch (m >> 2) {
			case 1: // IFluidHandler
				if (handlerCache != null) {
					IFluidHandler fluidHandler = handlerCache[bSide];
					if (fluidHandler != null && fluidHandler.fill(fluid, false) > 0)
						return fluidHandler.fill(fluid, true);
				}
				break;
			case 2: // TileEntityRednetCable
			case 0: // no mode
				// no-op
				break;
			}
		}
		return 0;
	}

	public void setGrid(FluidNetwork newGrid) {

		if (fluidForGrid == null)
			fluidForGrid = newGrid.storage.drain(0, false);
		_grid = newGrid;
	}

	@Override
	public void updateInternalTypes(IGridController grid) {

		if (grid != FluidNetwork.HANDLER) return;
		if (deadCache) {
			reCache();
			return;
		}
		boolean node = false;
		if (sideMode[6] != 1) {
			for (int i = 0; i < 6; i++) {
				final int t = sideMode[i];
				final int mode = t >> 2;
				node = ((t & 1) != 0) & (mode != 0) & (mode != 2) | node;
			}
		}
		isNode = node;
		if (_grid != null)
			_grid.addConduit(this);
		Packets.sendToAllPlayersWatching(this);
	}

	@Override
	public boolean shouldRenderCustomHitBox(int subHit, EntityPlayer player) {

		return subHit < 2;
	}

	@Override
	public CustomHitBox getCustomHitBox(int hit, EntityPlayer player) {

		final List<IndexedCuboid6> list = new ArrayList<>(7);
		addTraceableCuboids(list, true, MFRUtil.isHoldingUsableTool(player, pos), true);
		IndexedCuboid6 cube = list.get(0);
		cube.expand(0.003);
		Vector3 min = cube.min, max = cube.max.subtract(min);
		CustomHitBox box = new CustomHitBox(max.x, max.y, max.z, min.x, min.y, min.z);
		for (int i = 1, e = list.size(); i < e; ++i) {
			cube = list.get(i);
			if (shouldRenderCustomHitBox((Integer) cube.data, player)) {
				cube.subtract(min);
				if (cube.min.y < 0)
					box.sideLength[0] = Math.max(box.sideLength[0], -cube.min.y);
				if (cube.min.z < 0)
					box.sideLength[2] = Math.max(box.sideLength[2], -cube.min.z);
				if (cube.min.x < 0)
					box.sideLength[4] = Math.max(box.sideLength[4], -cube.min.x);
				cube.subtract(max);
				if (cube.max.y > 0)
					box.sideLength[1] = Math.max(box.sideLength[1], cube.max.y);
				if (cube.max.z > 0)
					box.sideLength[3] = Math.max(box.sideLength[3], cube.max.z);
				if (cube.max.x > 0)
					box.sideLength[5] = Math.max(box.sideLength[5], cube.max.x);
			}
		}
		for (int i = box.sideLength.length; i-- > 0;)
			box.drawSide[i] = box.sideLength[i] > 0;
		return box;
	}

	@Override
	public boolean onPartHit(EntityPlayer player, int subSide, int subHit) {

		if (subHit >= 0 && subHit < (2 + 6 * 2)) {
			if (MFRUtil.isHoldingUsableTool(player, pos)) {
				if (!worldObj.isRemote) {
					EnumFacing side = subSide < 6 ? EnumFacing.VALUES[subSide] : null;
					int data = sideMode[side != null ? side.getOpposite().ordinal() : 6] >> 2;
					byte mode = getMode(subSide);
					if (++mode == 2) ++mode;
					if (data == 2) {
						if (mode > 1)
							mode = 0;
						TileEntityPlasticPipe cable = MFRUtil.getTile(worldObj, pos.offset(side), TileEntityPlasticPipe.class);
						if (!isInterfacing(side)) {
							if (couldInterface(cable)) {
								mergeWith(cable, subSide);
								cable.onMerge();
								onMerge();
							}
						} else {
							removeFromGrid();
							final byte zero = 0;
							setMode(subSide, zero);
							cable.onMerge();
							onMerge();
							if (_grid == null)
								setGrid(new FluidNetwork(this));
						}
						return true;
					}
					else if (side == null) {
						if (mode > 1)
							mode = 0;
						setMode(subSide, mode);
						markDirty();
						switch (mode) {
						case 0:
							player.addChatMessage(new TextComponentTranslation("chat.info.mfr.rednet.tile.standard"));
							break;
						case 1:
							player.addChatMessage(new TextComponentTranslation("chat.info.mfr.rednet.tile.cableonly"));
							break;
						default:
						}
						return true;
					}
					if (mode > 3) {
						mode = 0;
					}
					setMode(subSide, mode);
					markDirty();
					switch (mode) {
					case 0:
						player.addChatMessage(new TextComponentTranslation("chat.info.mfr.fluid.connection.disabled"));
						break;
					case 1:
						player.addChatMessage(new TextComponentTranslation("chat.info.mfr.fluid.connection.output"));
						break;
					case 3:
						player.addChatMessage(new TextComponentTranslation("chat.info.mfr.fluid.connection.extract"));
						break;
					default:
					}
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void addTraceableCuboids(List<IndexedCuboid6> list, boolean forTrace, boolean hasTool, boolean offsetCuboids) {

		Vector3 offset = offsetCuboids ? Vector3.fromBlockPos(pos) : new Vector3(0, 0, 0);

		IndexedCuboid6 main = new IndexedCuboid6(0, subSelection[0]); // main body
		list.add(main);

		EnumFacing[] sides = EnumFacing.VALUES;
		boolean cableMode = sideMode[6] == 1;
		for (int i = sides.length; i-- > 0;) {
			int mode = sideMode[sides[i].getOpposite().ordinal()] >> 2;
			boolean iface = (mode > 0) & mode != 2;
			int o = 2 + i;
			if (((sideMode[sides[i].getOpposite().ordinal()] & 1) != 0) & mode > 0) {
				if (mode == 2) {
					o = 2 + 6 * 3 + i;
					list.add((IndexedCuboid6) new IndexedCuboid6(hasTool ? 2 + i : 1,
							subSelection[o]).setSide(i, i & 1).add(offset)); // cable part
					continue;
				}
				if (cableMode) continue;
				list.add((IndexedCuboid6) new IndexedCuboid6(o, subSelection[o]).add(offset)); // connection point
				o = 2 + 6 * 3 + i;
				list.add((IndexedCuboid6) new IndexedCuboid6(1, subSelection[o]).add(offset)); // cable part
			}
			else if (forTrace & hasTool) {
				if (!cableMode & iface) {
					list.add((IndexedCuboid6) new IndexedCuboid6(o, subSelection[o]).add(offset)); // connection point (raytrace)
				} else if (mode == 2) {
					o = 2 + 6 * 3 + i;
					list.add((IndexedCuboid6) new IndexedCuboid6(2 + i,
							subSelection[o]).setSide(i, i & 1).add(offset)); // cable part
				}
			}

		}
		main.add(offset);
	}

	@Override
	public void getTileInfo(List<ITextComponent> info, EnumFacing side, EntityPlayer player, boolean debug) {

		if (_grid != null) {
			info.add(text("Powered: " + isPowered));
			/* TODO: advanced monitoring
			if (isNode) {
				info.add("Throughput All: " + _grid.distribution);
				info.add("Throughput Side: " + _grid.distributionSide);
			} else//*/
			if (!debug) {
				info.add(text("Contains: " + StringHelper.getFluidName(_grid.storage.getFluid(), "<Empty>")));
				info.add(text("Saturation: " +
						(Math.ceil(_grid.storage.getFluidAmount() /
								(float) _grid.storage.getCapacity() * 1000) / 10f)));
			}
		} else if (!debug)
			info.add(text("Null Grid"));
		if (debug) {
			if (_grid != null) {
				info.add(text("Grid:" + _grid));
				info.add(text("    Conduits: " + _grid.getConduitCount() + ", Nodes: " + _grid.getNodeCount()));
				info.add(text("    Grid Max: " + _grid.storage.getCapacity() + ", Grid Cur: " +
						_grid.storage.getFluidAmount()));
				info.add(text("    Contains: " + StringHelper.getFluidName(_grid.storage.getFluid(), "<Empty>")));
			} else {
				info.add(text("Grid: Null"));
			}
			info.add(text("Cache: (" + Arrays.toString(handlerCache) + ")"));
			info.add(text("FluidForGrid: " + fluidForGrid));
			info.add(text("SideType: " + Arrays.toString(sideMode)));
			info.add(text("Node: " + isNode));
			return;
		}
	}

	@Override
	public void firstTick(IGridController grid) {

	}
	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {

		return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
	}

	@Override
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {

		if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
			return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new TileEntityPlasticPipe.PlasticPipeFluidHandler(facing));
		}

		return super.getCapability(capability, facing);
	}

	private class PlasticPipeFluidHandler implements IFluidHandler {

		private EnumFacing from;

		public PlasticPipeFluidHandler(EnumFacing from) {

			this.from = from;
		}

		@Override
		public IFluidTankProperties[] getTankProperties() {

			if (_grid == null)
				return FluidTankProperties.convert(FluidHelper.NULL_TANK_INFO);
			return new IFluidTankProperties[] { new FluidTankProperties(_grid.storage.getFluid(), _grid.storage.getCapacity()) };
		}

		@Override
		public int fill(FluidStack resource, boolean doFill) {

			if (_grid == null | sideMode[6] == 1) return 0;
			int t = sideMode[from.getOpposite().ordinal()];
			if (((t & 1) != 0) & isPowered & (t & 2) == 2)
			{
				return _grid.storage.fill(resource, doFill);
			}
			return 0;
		}

		@Nullable
		@Override
		public FluidStack drain(FluidStack resource, boolean doDrain) {

			if (_grid == null | sideMode[6] == 1) return null;
			int t = sideMode[from.getOpposite().ordinal()];
			if (((t & 1) != 0) & (t & 2) == 0)
			{
				return _grid.storage.drain(resource, doDrain);
			}
			return null;
		}

		@Nullable
		@Override
		public FluidStack drain(int maxDrain, boolean doDrain) {

			if (_grid == null | sideMode[6] == 1) return null;
			int t = sideMode[from.getOpposite().ordinal()];
			if (((t & 1) != 0) & (t & 2) == 0)
				return _grid.storage.drain(maxDrain, doDrain);
			return null;
		}
	}
}

