package powercrystals.minefactoryreloaded.block.transport;

import codechicken.lib.model.ModelRegistryHelper;
import codechicken.lib.model.blockbakery.BlockBakery;
import codechicken.lib.model.blockbakery.CCBakeryModel;
import codechicken.lib.model.blockbakery.IBakeryBlock;
import codechicken.lib.model.blockbakery.ICustomBlockBakery;
import codechicken.lib.raytracer.RayTracer;
import cofh.api.block.IBlockInfo;
import cofh.lib.util.helpers.ItemHelper;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.common.property.Properties;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import powercrystals.minefactoryreloaded.MFRRegistry;
import powercrystals.minefactoryreloaded.MineFactoryReloadedCore;
import powercrystals.minefactoryreloaded.block.BlockFactory;
import powercrystals.minefactoryreloaded.block.ItemBlockFactory;
import powercrystals.minefactoryreloaded.core.MFRUtil;
import powercrystals.minefactoryreloaded.core.UtilInventory;
import powercrystals.minefactoryreloaded.render.block.PlasticPipeRenderer;
import powercrystals.minefactoryreloaded.tile.transport.PlasticPipeUpgrade;
import powercrystals.minefactoryreloaded.tile.transport.TileEntityPlasticPipe;

import java.util.ArrayList;
import java.util.Random;

import static powercrystals.minefactoryreloaded.block.transport.BlockRedNetCable._subSideMappings;

public class BlockPlasticPipe extends BlockFactory implements IBlockInfo, IBakeryBlock {

	public static final IUnlistedProperty<ConnectionType>[] CONNECTION = new IUnlistedProperty[6];

	static {
		for (int i = 0; i < 6; i++) {
			CONNECTION[i] = Properties.toUnlisted(PropertyEnum.create("connection_" + i, ConnectionType.class));
		}
	}

	public BlockPlasticPipe() {

		super(0.8F);
		setUnlocalizedName("mfr.cable.plastic");
		providesPower = true;
		setRegistryName(MineFactoryReloadedCore.modId, "plastic_pipe");
	}

	@Override
	protected BlockStateContainer createBlockState() {

		BlockStateContainer.Builder builder = new BlockStateContainer.Builder(this);

		for (int i = 0; i < 6; i++) {
			builder.add(CONNECTION[i]);
		}
		return builder.build();
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {

		return PlasticPipeRenderer.INSTANCE
				.handleState((IExtendedBlockState) super.getExtendedState(state, world, pos), world.getTileEntity(pos));
	}

	@Override
	public BlockRenderLayer getBlockLayer() {

		return BlockRenderLayer.CUTOUT;
	}

	@Override
	public boolean activated(World world, BlockPos pos, EntityPlayer player, EnumFacing side, EnumHand hand, ItemStack heldItem) {

		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityPlasticPipe) {
			TileEntityPlasticPipe cable = (TileEntityPlasticPipe) te;

			harvesters.set(player);
			IBlockState state = world.getBlockState(pos);
			RayTraceResult part = collisionRayTrace(state, world, pos, RayTracer.getStartVec(player),
					RayTracer.getEndVec(player));
			harvesters.set(null);
			if (part == null)
				return false;

			int subHit = part.subHit;
			if (subHit < 0) {
				MineFactoryReloadedCore.instance().getLogger().error("subHit was " + subHit, new Throwable());
				return false;
			}
			int subSide = _subSideMappings[subHit];

			l2:
			if (cable.onPartHit(player, subSide, subHit)) {
				if (MFRUtil.isHoldingUsableTool(player, pos)) {
					MFRUtil.usedWrench(player, pos);
					return true;
				}
			} else if (PlasticPipeUpgrade.isUpgradeItem(heldItem)) {
				PlasticPipeUpgrade newUpgrade = PlasticPipeUpgrade.getUpgrade(heldItem);
				PlasticPipeUpgrade currentUpgrade = cable.getUpgrade();

				if (newUpgrade == currentUpgrade) {
					break l2;
				}

				if (currentUpgrade.getDrop() != null){
					UtilInventory.dropStackInAir(world, pos, currentUpgrade.getDrop());
				}
				if (!world.isRemote) {
					if (!player.capabilities.isCreativeMode) {
						ItemHelper.consumeItem(heldItem);
					}
					cable.setUpgrade(newUpgrade);
					neighborChanged(state, world, pos, Blocks.AIR);
					player.addChatMessage(new TextComponentTranslation(newUpgrade.getChatMessageKey()));
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public ArrayList<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {

		ArrayList<ItemStack> drops = new ArrayList<ItemStack>();

		Random rand = world instanceof World ? ((World) world).rand : RANDOM;

		ItemStack machine = new ItemStack(getItemDropped(state, rand, fortune), 1, damageDropped(state));
		drops.add(machine);

		TileEntity te = world.getTileEntity(pos);
		if (te instanceof TileEntityPlasticPipe) {
			PlasticPipeUpgrade upgrade = ((TileEntityPlasticPipe) te).getUpgrade();
			if(upgrade.getDrop() != null) {
				drops.add(upgrade.getDrop());
			}
		}

		return drops;
	}

	@Override
	public boolean isOpaqueCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean isFullCube(IBlockState state) {

		return false;
	}

	@Override
	public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {

		return false;
	}

	@Override
	public boolean hasTileEntity(IBlockState state) {

		return true;
	}

	@Override
	public TileEntity createTileEntity(World world, IBlockState state) {

		return new TileEntityPlasticPipe();
	}

	@Override
	public boolean preInit() {

		MFRRegistry.registerBlock(this, new ItemBlockFactory(this));
		GameRegistry.registerTileEntity(TileEntityPlasticPipe.class, "factoryPlasticPipe");
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerModels() {

		ModelLoader.setCustomStateMapper(this, new StateMapperBase() {

			@Override
			protected ModelResourceLocation getModelResourceLocation(IBlockState state) {

				return PlasticPipeRenderer.MODEL_LOCATION;
			}
		});

		ModelResourceLocation location = new ModelResourceLocation(getRegistryName(), "normal");
		ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), 0, location);

		ModelRegistryHelper.register(PlasticPipeRenderer.MODEL_LOCATION,
				new CCBakeryModel(MineFactoryReloadedCore.modId + ":blocks/tile.mfr.cable.plastic") {

					@Override
					public TextureAtlasSprite getParticleTexture() {

						return PlasticPipeRenderer.sprite;
					}
				});
		BlockBakery.registerBlockKeyGenerator(this,
				state -> state.getBlock().getRegistryName().toString() + "," + getConnectionTypesKey(state));

	}

	@Override
	public ICustomBlockBakery getCustomBakery() {

		return PlasticPipeRenderer.INSTANCE;
	}

	private String getConnectionTypesKey(IExtendedBlockState state) {

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < CONNECTION.length; i++) {
			sb.append(state.getValue(CONNECTION[i]).ordinal());
		}

		return sb.toString();
	}

	public enum ConnectionType implements IStringSerializable {

		NONE,
		CABLE,
		CABLE_DISCONNECTED,
		EXTRACT,
		EXTRACT_POWERED,
		OUTPUT,
		HANDLER_DISCONNECTED;

		@Override
		public String getName() {

			return this.name();
		}
	}
}
