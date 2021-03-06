package powercrystals.minefactoryreloaded.setup.village;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.fml.common.registry.VillagerRegistry;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerCareer;

import java.util.Random;

import net.minecraft.entity.passive.EntityVillager.ListItemForEmeralds;
import net.minecraft.entity.passive.EntityVillager.PriceInfo;
import net.minecraft.entity.passive.EntityVillager.ITradeList;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;

import powercrystals.minefactoryreloaded.MineFactoryReloadedCore;
import powercrystals.minefactoryreloaded.item.ItemSafariNet;
import powercrystals.minefactoryreloaded.setup.MFRThings;

public class Zoologist
{

	public static final VillagerProfession zoologistProfession = new VillagerProfession(MineFactoryReloadedCore.modId + ":zoologist", MineFactoryReloadedCore.modId + ":textures/villager/zoologist.png", "minecraft:/textures/entity/zombie_villager/zombie_villager.png");

	public static void init()
	{
		VillagerRegistry.instance().register(zoologistProfession);

		VillagerCareer zoologist = new VillagerCareer(zoologistProfession, MineFactoryReloadedCore.modId + ":zoologist");
		zoologist.addTrade(1, new ListItemForEmeralds(MFRThings.safariNetSingleItem, new PriceInfo(1, 1)));
		zoologist.addTrade(1, new ListItemForEmeralds(MFRThings.safariNetItem, new PriceInfo(3, 1)));
		zoologist.addTrade(2, new ListItemForEmeraldAndItem(getHiddenNetStack(), MFRThings.safariNetSingleItem));
		zoologist.addTrade(2, new ListItemForEmeraldAndItem(new ItemStack(MFRThings.rubberSaplingBlock, 8, 0), ItemBlock.getItemFromBlock(Blocks.SAPLING), 8, 6));
	}

	public static ItemStack getHiddenNetStack()
	{
		ItemStack s = new ItemStack(MFRThings.safariNetSingleItem);
		return ItemSafariNet.makeMysteryNet(s);
	}

	private static class ListItemForEmeraldAndItem implements EntityVillager.ITradeList
	{

		private final ItemStack itemToBuy;
		private final Item itemToPay;
		private final int maxRandomMeta;
		private final int payCount;

		public ListItemForEmeraldAndItem(ItemStack itemToBuy, Item itemToPay, int payCount, int maxRandomMeta)
		{
			this.itemToBuy = itemToBuy;
			this.itemToPay = itemToPay;
			this.payCount = payCount;
			this.maxRandomMeta = maxRandomMeta;
		}

		public ListItemForEmeraldAndItem(ItemStack itemToBuy, Item itemToPay)
		{
			this(itemToBuy, itemToPay, 1, 0);
		}

		@Override
		public void modifyMerchantRecipeList(MerchantRecipeList recipeList, Random random)
		{
			ItemStack itemCost1;
			ItemStack itemCost2;
			ItemStack itemBeingSold;

			itemCost1 = new ItemStack(Items.EMERALD);
			itemCost2 = new ItemStack(itemToPay, 1, maxRandomMeta == 0 ? 0 : random.nextInt(maxRandomMeta));
			itemBeingSold = itemToBuy.copy();

			recipeList.add(new MerchantRecipe(itemCost1, itemCost2, itemBeingSold));
		}
	}

}
