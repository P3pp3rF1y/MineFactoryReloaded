package powercrystals.minefactoryreloaded.net;

import cofh.core.render.IModelRegister;
import net.minecraft.block.Block;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.LoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import powercrystals.minefactoryreloaded.MineFactoryReloadedClient;
import powercrystals.minefactoryreloaded.MineFactoryReloadedCore;
import powercrystals.minefactoryreloaded.render.IColorRegister;
import powercrystals.minefactoryreloaded.tile.machine.TileEntityChunkLoader;

public class CommonProxy implements LoadingCallback
{
	public static LinkedList<Ticket> ticketsInLimbo = new LinkedList<Ticket>();

	public static boolean loadTicket(Ticket ticket, boolean addToList)
	{
		int x = ticket.getModData().getInteger("X");
		int y = ticket.getModData().getInteger("Y");
		int z = ticket.getModData().getInteger("Z");

		TileEntity tile = ticket.world.getTileEntity(new BlockPos(x, y, z));
		if (!(tile instanceof TileEntityChunkLoader))
		{
			ForgeChunkManager.releaseTicket(ticket);
			return true;
		}
		boolean r = ((TileEntityChunkLoader)tile).receiveTicket(ticket);
		if (addToList & !r)
			ticketsInLimbo.push(ticket);
		return r;
	}

	@Override
	public void ticketsLoaded(List<Ticket> tickets, World world)
	{
		for (Ticket ticket : tickets)
			loadTicket(ticket, true);
	}

	public EntityPlayer getPlayer() {

		return null;
	}

	public void preInit() {}
	
	public void init()
	{
		FMLCommonHandler.instance().bus().register(GridTickHandler.energy);
		FMLCommonHandler.instance().bus().register(GridTickHandler.redstone);
		FMLCommonHandler.instance().bus().register(GridTickHandler.fluid);
		FMLCommonHandler.instance().bus().register(new ConnectionHandler());
		ForgeChunkManager.setForcedChunkLoadingCallback(MineFactoryReloadedCore.instance(), this);
	}

	public void addModelRegister(IModelRegister register) {}
	
	public void addColorRegister(IColorRegister register) {}

	public void movePlayerToCoordinates(EntityLivingBase e, double x, double y, double z)
	{
		if (e instanceof EntityPlayerMP)
		{
			EntityPlayerMP ep = (EntityPlayerMP)e;
			ep.connection.setPlayerLocation(x, y, z, ep.cameraYaw, ep.cameraPitch);
		}
		e.setPositionAndUpdate(x, y, z);
	}

	public void relightChunk(Chunk chunk)
	{
		if (chunk != null)
		{
			chunk.generateSkylightMap();
			ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
			for (int i = storage.length; i --> 0;)
				if (storage[i] != null)
				{
					//{ spigot compat: force data array to exist
					NibbleArray a = storage[i].getSkylightArray();
					if(a != null)
					{
						a.set(0, 0, 0, 0);
						a.set(0, 0, 0, 15);
						//}
						Arrays.fill(a.getData(), (byte)0);
					}
				}
			chunk.resetRelightChecks();
			chunk.setModified(true);
			World world = chunk.getWorld();
			if (world instanceof WorldServer)
			{
				PlayerChunkMap chunkMap = ((WorldServer) world).getPlayerChunkMap();
				if (chunkMap == null)
					return;
				PlayerChunkMapEntry entry = chunkMap.getEntry(chunk.xPosition, chunk.zPosition);

				if (entry != null)
					entry.sendPacket(new SPacketChunkData(chunk, -1));
			}
		}
	}
}
