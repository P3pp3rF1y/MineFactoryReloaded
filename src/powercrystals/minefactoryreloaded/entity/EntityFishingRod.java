package powercrystals.minefactoryreloaded.entity;

import cofh.lib.util.helpers.MathHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import net.minecraftforge.fml.relauncher.SideOnly;
import powercrystals.minefactoryreloaded.setup.MFRConfig;

import java.util.Iterator;

public class EntityFishingRod extends EntityThrowable {
	public int fuse;

	public EntityFishingRod(World world) {
		super(world);
		setSize(0.10F, 0.25F);
	}

	public EntityFishingRod(World world, EntityLivingBase entity) {
		super(world, entity);
		setSize(0.10F, 0.25F);
		setAir(0);
		fuse = 40;
	}

	@Override
	public void onUpdate() {
		prevPosX = posX;
		prevPosY = posY;
		prevPosZ = posZ;
		motionY -= 0.03999999910593033D;
		moveEntity(motionX, motionY, motionZ);
		motionX *= 0.9800000190734863D;
		motionY *= 0.9800000190734863D;
		motionZ *= 0.9800000190734863D;

		if (onGround) {
			motionX *= 0.699999988079071D;
			motionZ *= 0.699999988079071D;
			motionY *= -0.5D;
		}

		if (worldObj.isRemote) {
			worldObj.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, posX, posY + 0.25, posZ, 0, 0, 0);
		} else if (fuse-- <= 0) {
			explode();
			setDead();
		} else if (fuse == 1) {
			if (worldObj instanceof WorldServer) {
				((WorldServer)worldObj).setEntityState(this, (byte)18);
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void handleStatusUpdate(byte id) {
		super.handleStatusUpdate(id);
		if (id == 18) {
			IBlockState state = worldObj.getBlockState(new BlockPos((int) Math.floor(posX), (int) Math.floor(posY + 0.25), (int) Math.floor(posZ)));
			Block block = state.getBlock();
			if (block.isAssociatedBlock(Blocks.WATER) || block.isAssociatedBlock(Blocks.FLOWING_WATER)) {
				double f = 0.75;
				for (int j = 60; j --> 0; ) {
					double y = MathHelper.cos(j * Math.PI / 180) * 0.75;
					double m = MathHelper.sin((j * (.35)) * Math.PI / 180);
					for (int i = 60; i --> 0; ) {
						double x = MathHelper.cos((i * 6) * Math.PI / 180) * m;
						double z = MathHelper.sin((i * 6) * Math.PI / 180) * m;
						worldObj.spawnParticle(EnumParticleTypes.BLOCK_DUST, posX, posY + 0.25, posZ, x * f, y * f, z * f,
								Block.getStateId(Blocks.WATER.getDefaultState()));
					}
				}
			}
		}
	}

	private void explode() {
		float f = 2.5F;
		worldObj.createExplosion(this, this.posX, this.posY, this.posZ, f, true);
		int rate = MFRConfig.fishingDropRate.getInt();
		for (float x = (float)(posX - f); x < posX + f; ++x)
			for (float y = (float)(posY - f); y < posY + f; ++y)
				for (float z = (float)(posZ - f); z < posZ + f; ++z) {
					Block block = worldObj.getBlockState(new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z))).getBlock();
					if (block.isAssociatedBlock(Blocks.WATER) || block.isAssociatedBlock(Blocks.FLOWING_WATER))
						if (rand.nextInt(rate) == 0) {
							LootContext.Builder builder = new LootContext.Builder((WorldServer)this.worldObj);
							Iterator iterator = this.worldObj.getLootTableManager().getLootTableFromLocation(LootTableList.GAMEPLAY_FISHING_FISH).
									generateLootForPools(rand, builder.build()).iterator();

							while (iterator.hasNext()) {
								ItemStack stack = (ItemStack) iterator.next();
								ItemStack smelted;
								if (rand.nextInt(30) == 0 && ((smelted = FurnaceRecipes.instance().getSmeltingResult(stack)) != null)) {
									stack = smelted;
								}
								EntityItem e = new EntityItem(worldObj, x, y, z, stack);
								e.motionX = rand.nextGaussian() / 2;
								e.motionZ = rand.nextGaussian() / 2;
								e.motionY = 0.4 + (rand.nextDouble() - 0.4) / 2;
								worldObj.spawnEntityInWorld(e);
							}
						}
				}
	}

	@Override
	public void writeEntityToNBT(NBTTagCompound tag) {
		super.writeEntityToNBT(tag);
		tag.setByte("Fuse", (byte)fuse);
	}

	@Override
	public void readEntityFromNBT(NBTTagCompound tag) {
		super.readEntityFromNBT(tag);
		fuse = tag.getByte("Fuse");
	}

	@Override
	protected void onImpact(RayTraceResult rayTraceResult) { }

	// TODO: override moveEntity, handle water movement

}
