package powercrystals.minefactoryreloaded.tile.machine;

import cofh.core.fluid.FluidTankCore;
import cofh.lib.util.helpers.ItemHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import powercrystals.minefactoryreloaded.core.UtilInventory;
import powercrystals.minefactoryreloaded.gui.client.GuiAutoBrewer;
import powercrystals.minefactoryreloaded.gui.client.GuiFactoryInventory;
import powercrystals.minefactoryreloaded.gui.container.ContainerAutoBrewer;
import powercrystals.minefactoryreloaded.gui.container.ContainerFactoryPowered;
import powercrystals.minefactoryreloaded.setup.MFRConfig;
import powercrystals.minefactoryreloaded.setup.Machine;
import powercrystals.minefactoryreloaded.tile.base.TileEntityFactoryPowered;

import javax.annotation.Nullable;

public class TileEntityAutoBrewer extends TileEntityFactoryPowered {

	protected boolean _inventoryDirty;

	protected byte[] spareResources;

	public TileEntityAutoBrewer() {

		super(Machine.AutoBrewer);
		spareResources = new byte[getSizeInventory() / 5];
		setManageSolids(true);
		_tanks[0].setLock(FluidRegistry.WATER);
	}

	private int getProcessSlot(int row) {

		return row * 5;
	}

	private int getTemplateSlot(int row) {

		return row * 5 + 1;
	}

	private int getResourceSlot(int row, int slot) {

		return row * 5 + slot + 2;
	}

	@Override
	protected FluidTankCore[] createTanks() {

		return new FluidTankCore[] { new FluidTankCore(BUCKET_VOLUME * 4) };
	}

	@Override
	public int getSizeInventory() {

		// 6 sets of: process, template, res, res, res
		// 30 is output, 31 is empty bottle input
		return 32;
	}

	@Override
	public ContainerFactoryPowered getContainer(InventoryPlayer inventoryPlayer) {

		return new ContainerAutoBrewer(this, inventoryPlayer);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public GuiFactoryInventory getGui(InventoryPlayer inventoryPlayer) {

		return new GuiAutoBrewer(getContainer(inventoryPlayer), this);
	}

	@Override
	protected boolean activateMachine() {

		boolean hasWorkToDo = false, didWork = false;
		boolean doingWork = getWorkDone() > 0;
		if (doingWork & !_inventoryDirty)
			hasWorkToDo = true;
		else {
			final int waterCost = MFRConfig.autobrewerFluidCost.getInt();
			for (int row = 0; row < 6; row++) {
				int processSlot = getProcessSlot(row), templateSlot = getTemplateSlot(row);
				if (_inventory[31] != null && _inventory[processSlot] == null && _inventory[templateSlot] != null) {
					if (row == 0 || _inventory[getTemplateSlot(row - 1)] == null) {
						ItemStack waterBottle = new ItemStack(Items.POTIONITEM);
						if (getPotionResult(waterBottle, _inventory[templateSlot]) != waterBottle)
							if (drain(waterCost, false, _tanks[0]) == waterCost) {
								drain(waterCost, true, _tanks[0]);
								_inventory[31] = ItemHelper.consumeItem(_inventory[31]);
								_inventory[processSlot] = new ItemStack(Items.POTIONITEM);
								didWork = true;
							}
					}
				}
				if (_inventory[processSlot] != null) {
					if (_inventory[getProcessSlot(row + 1)] == null && canBrew(row))
						hasWorkToDo = true;
				}
			}
			_inventoryDirty = false;
		}

		if (!hasWorkToDo) {
			setWorkDone(0);
			setIdleTicks(getIdleTicksMax());
			return didWork;
		}

		if (getWorkDone() < getWorkMax()) {
			return incrementWorkDone();
		}

		setWorkDone(0);

		for (int row = 6; row-- > 0;) {
			ItemStack current = _inventory[getProcessSlot(row)];
			ItemStack next = _inventory[getProcessSlot(row + 1)];
			if (next != null && current != null) {
				continue;
				// no exiting early, we know there's a potion that can be moved/brewed
			}

			ItemStack ingredient = _inventory[getTemplateSlot(row)];

			if (current != null) {
				if (ingredient == null) {
					_inventory[getProcessSlot(row + 1)] = current;
					_inventory[getProcessSlot(row)] = null;
					continue;
				}
				for (int i = 0; i < 3; i++) {
					int slot = getResourceSlot(row, i);
					if (spareResources[row] <= 0 && !UtilInventory.stacksEqual(_inventory[slot], ingredient)) {
						continue;
					}

					ItemStack newPotion = this.getPotionResult(current, ingredient); // TODO: are there mods we need to be concerned about?
					// if they modify the ingredient stack, we need either defensive copies, or to pass the storage slot stack

					if (newPotion != null && current != newPotion) {
						_inventory[getProcessSlot(row + 1)] = newPotion;
					} else {
						_inventory[getProcessSlot(row + 1)] = current;
					}

					_inventory[getProcessSlot(row)] = null;

					if (current == newPotion)
						break;

					if (spareResources[row] > 0) {
						--spareResources[row];
						break;
					}
					--_inventory[slot].stackSize;
					++spareResources[row];
					if (ingredient.getItem().hasContainerItem(_inventory[slot])) {
						ItemStack r = ingredient.getItem().getContainerItem(_inventory[slot]);
						if (r != null && r.isItemStackDamageable() && r.getItemDamage() > r.getMaxDamage())
							r = null;
						_inventory[slot] = r;
					}
					if (_inventory[slot] != null && _inventory[slot].stackSize <= 0)
						_inventory[slot] = null;
					break;
				}
			}
		}
		return true;
	}

	private boolean canBrew(int row) {

		if (_inventory[getTemplateSlot(row)] == null) {
			return false;
		}

		ItemStack ingredient = _inventory[getTemplateSlot(row)];

		if (!BrewingRecipeRegistry.isValidIngredient(ingredient)) {
			return false;
		}

		boolean hasIngredients = spareResources[row] > 0;
		if (!hasIngredients) for (int i = 0; i < 3; i++) {
			if (UtilInventory.stacksEqual(ingredient, _inventory[getResourceSlot(row, i)])) {
				hasIngredients = true;
				break;
			}
		}
		if (!hasIngredients) {
			return false;
		}

		if (_inventory[getProcessSlot(row)] != null) {
			ItemStack newPotion = this.getPotionResult(_inventory[getProcessSlot(row)], ingredient);

			if (newPotion != null && _inventory[getProcessSlot(row)] != newPotion) {
				return true; //existingPotion != newPotion;
				// push potions without effects that have been previously brewed on through
			}
		}

		return false;
	}

	private ItemStack getPotionResult(ItemStack existingPotion, ItemStack ingredient) {

		if (ingredient == null || !BrewingRecipeRegistry.isValidIngredient(ingredient)) {
			return existingPotion;
		}
		return BrewingRecipeRegistry.getOutput(existingPotion, ingredient);
	}

	@Override
	public int getWorkMax() {

		return 160;
	}

	@Override
	public int getIdleTicksMax() {

		return 10;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack itemstack, EnumFacing side) {

		int row = slot / 5;
		int column = slot % 5;

		if (itemstack == null) return false;
		if (slot == 31) return itemstack.getItem().equals(Items.GLASS_BOTTLE);
		if (row == 6) return false;
		if (column == 1) return false;
		if (column == 0) return _inventory[getTemplateSlot(row)] != null &&
				BrewingRecipeRegistry.isValidInput(itemstack) &&
				(row == 0 || _inventory[getTemplateSlot(row - 1)] == null);
		return ingredientsEqual(_inventory[getTemplateSlot(row)], itemstack);
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack itemstack, EnumFacing side) {

		int row = slot / 5;
		int column = slot % 5;

		if (row == 6) return slot != 31;
		if (column == 1) return false;
		if (column == 0) return _inventory[getTemplateSlot(row)] == null;
		return !ingredientsEqual(_inventory[getTemplateSlot(row)], itemstack);
	}

	private boolean ingredientsEqual(ItemStack template, ItemStack ingredient) {

		if (template == null | ingredient == null || !BrewingRecipeRegistry.isValidIngredient(template)) {
			return false;
		}
		return PotionUtils.getEffectsFromStack(template).equals(PotionUtils.getEffectsFromStack(ingredient));
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack itemstack) {

		if (itemstack != null && !shouldDropSlotWhenBroken(slot)) {
			itemstack.stackSize = 1;
			if (!ingredientsEqual(_inventory[slot], itemstack)) {
				spareResources[slot / 5] = 0;
			}
		}
		super.setInventorySlotContents(slot, itemstack);
	}

	@Override
	protected void onFactoryInventoryChanged() {

		super.onFactoryInventoryChanged();
		_inventoryDirty = true;
	}

	@Override
	public boolean shouldDropSlotWhenBroken(int slot) {

		return slot % 5 != 1 || slot == 31;
	}

	@Override
	public void writePortableData(EntityPlayer player, NBTTagCompound tag) {

		// TODO: read/write template slots
	}

	@Override
	public void readPortableData(EntityPlayer player, NBTTagCompound tag) {

	}

	@Override
	public boolean allowBucketDrain(EnumFacing facing, ItemStack stack) {

		return !stack.getItem().equals(Items.GLASS_BOTTLE);
	}

	@Override
	public boolean allowBucketFill(EnumFacing facing, ItemStack stack) {

		return !stack.getItem().equals(Items.POTIONITEM);
	}

	@Override
	protected boolean canDrainTank(EnumFacing facing, int index) {

		return false;
	}

	@Nullable
	@Override
	public FluidStack drain(EnumFacing facing, FluidStack resource, boolean doDrain) {

		return null;
	}

	@Nullable
	@Override
	public FluidStack drain(EnumFacing facing, int maxDrain, boolean doDrain) {

		return null;
	}

}
