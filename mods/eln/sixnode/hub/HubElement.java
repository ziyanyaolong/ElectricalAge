package mods.eln.sixnode.hub;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import mods.eln.Eln;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeElectricalLoad;
import mods.eln.node.SixNode;
import mods.eln.node.SixNodeDescriptor;
import mods.eln.node.SixNodeElement;
import mods.eln.node.SixNodeElementInventory;
import mods.eln.sim.ElectricalConnection;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.mna.component.Component;
import mods.eln.sim.mna.component.Resistor;
import mods.eln.sim.process.destruct.ResistorCurrentWatchdog;
import mods.eln.sim.process.destruct.VoltageStateWatchDog;
import mods.eln.sim.process.destruct.WorldExplosion;
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class HubElement extends SixNodeElement {

	public HubElement(SixNode sixNode, Direction side,
			SixNodeDescriptor descriptor) {
		super(sixNode, side, descriptor);

		for (int idx = 0; idx < 4; idx++) {
			electricalLoad[idx] = new NodeElectricalLoad("electricalLoad" + idx);
			electricalLoadList.add(electricalLoad[idx]);
		}

		for (int idx = 0; idx < 6; idx++) {
			connectionGrid[idx] = false;
		}

	}

	NodeElectricalLoad[] electricalLoad = new NodeElectricalLoad[4];
	boolean[] connectionGrid = new boolean[6];

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		// TODO Auto-generated method stub
		super.readFromNBT(nbt);
		for (int idx = 0; idx < 6; idx++) {
			connectionGrid[idx] = nbt.getBoolean( "connectionGrid" + idx);
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);
		for (int idx = 0; idx < 6; idx++) {
			nbt.setBoolean("connectionGrid" + idx, connectionGrid[idx]);
		}
	}

	@Override
	public IInventory getInventory() {
		// TODO Auto-generated method stub
		return inventory;
	}

	@Override
	public ElectricalLoad getElectricalLoad(LRDU lrdu) {
		if (inventory.getStackInSlot(HubContainer.cableSlotId + lrdu.toInt()) != null)
			return electricalLoad[lrdu.toInt()];
		return null;
	}

	@Override
	public ThermalLoad getThermalLoad(LRDU lrdu) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getConnectionMask(LRDU lrdu) {
		if (getElectricalLoad(lrdu) != null)
			return NodeBase.maskElectricalAll;

		return 0;
	}

	@Override
	public String multiMeterString() {
		// TODO Auto-generated method stub
		return "";// Utils.plotVolt("U:",electricalLoad.Uc) +
					// Utils.plotAmpere("I:",electricalLoad.getCurrent());
	}

	@Override
	public String thermoMeterString() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	public void networkSerialize(DataOutputStream stream) {
		// TODO Auto-generated method stub
		super.networkSerialize(stream);
		try {
			for (int idx = 0; idx < 4; idx++) {
				Utils.serialiseItemStack(stream, inventory
						.getStackInSlot(HubContainer.cableSlotId + idx));
			}

			for (int idx = 0; idx < 6; idx++) {
				stream.writeBoolean(connectionGrid[idx]);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void initialize() {
		setup();
		for (int idx = 0; idx < 4; idx++) {
			Eln.applySmallRs(electricalLoad[idx]);
		}
	}

	@Override
	protected void inventoryChanged() {
		super.inventoryChanged();
		sixNode.disconnect();
		setup();
		sixNode.connect();

	}
	

	void setup() {
		slowProcessList.clear();
		WorldExplosion exp = new WorldExplosion(this);
		exp.cableExplosion();
		
		for(Component c : electricalComponentList){
			Resistor r = (Resistor)c;
			r.breakConnection();
		}
		electricalComponentList.clear();
		
		for(LRDU lrdu : LRDU.values()){
			ElectricalCableDescriptor d = getCableDescriptorFromLrdu(lrdu);
			if(d == null) continue;
			
			VoltageStateWatchDog watchdog = new VoltageStateWatchDog();
			slowProcessList.add(watchdog);
			watchdog	
				.setUNominal(d.electricalNominalVoltage)
				.set(electricalLoad[lrdu.toInt()])
				.set(exp);			
			
		}

		for (int idx = 0; idx < 6; idx++) {
			if (connectionGrid[idx]) {
				LRDU[] lrdu = connectionIdToSide(idx);
				
				if(inventory.getStackInSlot(HubContainer.cableSlotId + lrdu[0].toInt()) != null && inventory.getStackInSlot(HubContainer.cableSlotId + lrdu[1].toInt()) != null){
					
					Resistor r = new Resistor(electricalLoad[lrdu[0].toInt()], electricalLoad[lrdu[1].toInt()]);
					r.setR(getCableDescriptorFromLrdu(lrdu[0]).electricalRs + getCableDescriptorFromLrdu(lrdu[1]).electricalRs);
					electricalComponentList.add(r);
					
					ResistorCurrentWatchdog watchdog = new ResistorCurrentWatchdog();
					slowProcessList.add(watchdog);
					watchdog	
						.set(r)
						.setIAbsMax(Math.min(getCableDescriptorFromLrdu(lrdu[0]).electricalMaximalCurrent, getCableDescriptorFromLrdu(lrdu[1]).electricalMaximalCurrent))
						.set(exp);
				}
			}
		}
	}
	
	ElectricalCableDescriptor getCableDescriptorFromLrdu(LRDU lrdu){
		ElectricalCableDescriptor cableDescriptor;
		ItemStack cable;
		cable = inventory
				.getStackInSlot(HubContainer.cableSlotId
						+ lrdu.toInt());
		cableDescriptor = (ElectricalCableDescriptor) Eln.sixNodeItem
				.getDescriptor(cable);
		return cableDescriptor;
	}

	static LRDU[] connectionIdToSide(int id) {
		switch (id) {
		case 0:
			return new LRDU[] { LRDU.Left, LRDU.Down };
		case 1:
			return new LRDU[] { LRDU.Right, LRDU.Up };
		case 2:
			return new LRDU[] { LRDU.Down, LRDU.Right };
		case 3:
			return new LRDU[] { LRDU.Up, LRDU.Left };
		case 4:
			return new LRDU[] { LRDU.Left, LRDU.Right };
		case 5:
			return new LRDU[] { LRDU.Down, LRDU.Up };
		}

		return null;
	}

	@Override
	public boolean hasGui() {
		return true;
	}

	@Override
	public Container newContainer(Direction side, EntityPlayer player) {
		return new HubContainer(player, inventory);
	}

	SixNodeElementInventory inventory = new SixNodeElementInventory(4, 64, this);

	@Override
	public boolean onBlockActivated(EntityPlayer entityPlayer, Direction side,
			float vx, float vy, float vz) {
		// TODO Auto-generated method stub
		return false;
	}

	
	public static final byte clientConnectionGridToggle = 1;
	
	@Override
	public void networkUnserialize(DataInputStream stream) {
		super.networkUnserialize(stream);
		try {
			switch(stream.readByte()){
			case clientConnectionGridToggle:
				int id = stream.readByte();
				connectionGrid[id] = ! connectionGrid[id];
				sixNode.disconnect();
				setup();
				sixNode.connect();
				needPublish();
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
