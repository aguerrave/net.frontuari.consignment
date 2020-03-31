package net.frontuari.grid;

import org.compiere.apps.IStatusBar;
import org.compiere.grid.CreateFrom;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;

public class FTUConfirmnReceipt extends CreateFrom  {

	public FTUConfirmnReceipt(GridTab gridTab) {
		super(gridTab);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Object getWindow() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean dynInit() throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void info(IMiniTable miniTable, IStatusBar statusBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean save(IMiniTable miniTable, String trxName) {
		// TODO Auto-generated method stub
		return false;
	}

}
