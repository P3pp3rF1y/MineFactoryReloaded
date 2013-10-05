package powercrystals.minefactoryreloaded.gui.client;

import powercrystals.minefactoryreloaded.gui.container.ContainerSewer;
import powercrystals.minefactoryreloaded.tile.machine.TileEntitySewer;

public class GuiSewer extends GuiFactoryInventory
{
	public GuiSewer(ContainerSewer container, TileEntitySewer tileentity)
	{
		super(container, tileentity);
		ySize = 180;
		_tanksOffsetX = 152;
	}
}
