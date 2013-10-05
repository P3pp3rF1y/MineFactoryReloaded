package powercrystals.minefactoryreloaded.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import powercrystals.minefactoryreloaded.MFRRegistry;

public class ItemFactoryCup extends ItemFactory implements IFluidContainerItem
{
	private int _maxUses = 0;
	private boolean _prefix = false;

	public ItemFactoryCup(int id, int stackSize, int maxUses)
	{
		super(id);
		this.setMaxStackSize(stackSize);
		this._maxUses = maxUses;
		this.setMaxDamage(maxUses);
		this.setHasSubtypes(true);
	}

	@Override
	public String getUnlocalizedName(ItemStack stack)
	{
		if (getFluid(stack) != null)
			return getUnlocalizedName() + (_prefix ? ".prefix" : ".suffix");
		return getUnlocalizedName();
	}

	public String getLocalizedName(String str)
	{
		String name = getUnlocalizedName() + "." + str;
		if (StatCollector.func_94522_b(name))
			return StatCollector.translateToLocal(name);
		return null;
	}

	@Override
	public String getItemDisplayName(ItemStack item)
	{
		int id = item.getItemDamage();
		if (id != 0)
		{
			String ret = getFluidName(item), t = getLocalizedName(ret);
			if (t != null && !t.isEmpty())
				return EnumChatFormatting.RESET + t + EnumChatFormatting.RESET;
			if (ret == null)
			{
				item.setItemDamage(0);
				return super.getItemDisplayName(item);
			}
			Fluid liquid = FluidRegistry.getFluid(ret);
			if (liquid != null)
			{
				ret = liquid.getLocalizedName();
			}
			_prefix = true;
			t = super.getItemDisplayName(item);
			_prefix = false;
			t = t != null ? t.trim() : "";
			ret = (t.isEmpty() ? "" : t + " ") + ret;
			t = super.getItemDisplayName(item);
			t = t != null ? t.trim() : "";
			ret += t.isEmpty() ? " Cup" : " " + t;
			return ret;
		}
		return super.getItemDisplayName(item);
	}

	@Override
	public ItemStack getContainerItemStack(ItemStack stack)
	{
		ItemFactoryCup item = (ItemFactoryCup)stack.getItem();
		int damage = stack.getItemDamage() + 1;
		if (item == null || damage >= item._maxUses)
			return null;
		stack = new ItemStack(item, 1, 0);
		stack.setItemDamage(damage);
		return stack;
	}

	public String getFluidName(ItemStack stack)
	{
		NBTTagCompound tag = stack.stackTagCompound;
		return tag == null || !tag.hasKey("fluid") ? null : tag.getCompoundTag("fluid").getString("FluidName");
	}
	
	@Override
	public FluidStack getFluid(ItemStack stack)
	{
		NBTTagCompound tag = stack.stackTagCompound;
		FluidStack fluid = null;
		if (tag != null && tag.hasKey("fluid"))
		{
			fluid = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("fluid"));
			if (fluid == null)
				tag.removeTag("fluid");
		}
		return fluid;
	}

	@Override
	public int getCapacity(ItemStack container)
	{
		return FluidContainerRegistry.BUCKET_VOLUME;
	}

	@Override
	public int fill(ItemStack stack, FluidStack resource, boolean doFill)
	{
		if (resource == null || resource.getFluid().isGaseous())
			return 0;
		int fillAmount = 0, capacity = getCapacity(stack);
		NBTTagCompound tag = stack.stackTagCompound, fluidTag = null;
		FluidStack fluid = null;
		if (tag == null || !tag.hasKey("fluid") ||
			(fluidTag = tag.getCompoundTag("fluid")) == null ||
			(fluid = FluidStack.loadFluidStackFromNBT(fluidTag)) == null)
			fillAmount = Math.min(capacity, resource.amount);
		if (fluid == null)
		{
			if (doFill)
				fluid = resource.copy();
		}
		else if (!fluid.isFluidEqual(resource))
			return 0;
		else
			fillAmount = Math.min(capacity - fluid.amount, resource.amount);
		fillAmount = Math.max(fillAmount, 0);
		if (doFill)
		{
			if (tag == null)
				tag = stack.stackTagCompound = new NBTTagCompound();
			fluid.amount = fillAmount;
			tag.setTag("fluid", fluid.writeToNBT(fluidTag == null ? new NBTTagCompound() : fluidTag));
		}
		return fillAmount;
	}

	@Override
	public FluidStack drain(ItemStack stack, int maxDrain, boolean doDrain)
	{
		NBTTagCompound tag = stack.stackTagCompound, fluidTag = null;
		FluidStack fluid = null;
		if (tag == null || !tag.hasKey("fluid") ||
			(fluidTag = tag.getCompoundTag("fluid")) == null ||
			(fluid = FluidStack.loadFluidStackFromNBT(fluidTag)) == null)
			return null;
		int drainAmount = (int)(Math.min(maxDrain, fluid.amount) * (Math.max(Math.random() - 0.25, 0) + 0.25));
		if (doDrain)
		{
			if (tag.hasKey("toDrain"))
			{
				drainAmount = tag.getInteger("toDrain");
				tag.removeTag("toDrain");
			}
			tag.removeTag("fluid");
		}
		else
			tag.setInteger("toDrain", drainAmount);
		fluid.amount = drainAmount;
		return fluid;
	}

	public boolean hasDrinkableLiquid(ItemStack stack)
	{
		return MFRRegistry.getLiquidDrinkHandlers().containsKey(getFluidName(stack));
	}

	@Override
	public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player)
	{
		ItemFactoryCup item = (ItemFactoryCup)stack.getItem();
		if (item == null)
			return null; // sanity check

		if (hasDrinkableLiquid(stack))
			MFRRegistry.getLiquidDrinkHandlers().get(getFluidName(stack)).onDrink(player);

		if(!player.capabilities.isCreativeMode)
		{
			ItemStack drop = item.getContainerItemStack(stack);
			if (drop != null)
			{
				if (stack.stackSize-- > 1)
				{
					if (!player.inventory.addItemStackToInventory(drop))
						player.dropPlayerItem(drop);
				}
				else if (stack.stackSize == 0)
					return drop;
			}
			else
			{
				--stack.stackSize;
				player.renderBrokenItemStack(stack);
				player.addStat(StatList.objectBreakStats[item.itemID], 1);
			}
		}

		if (stack.stackSize <= 0)
			stack.stackSize = 0;
		return stack;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack)
	{
		return 32;
	}

	@Override
	public EnumAction getItemUseAction(ItemStack stack)
	{
		return hasDrinkableLiquid(stack) ? EnumAction.drink : EnumAction.none;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (hasDrinkableLiquid(stack))
			player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		return stack;
	}
}
