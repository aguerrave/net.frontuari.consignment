package net.frontuari.model;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Properties;

import org.compiere.model.MInventoryLine;
import org.compiere.model.MInvoiceLine;

public class MFTUMatchPOConsignment extends X_FTU_MatchPOConsignment {

	/**
	 * 
	 */
	private static final long serialVersionUID = 20754876975938684L;

	public MFTUMatchPOConsignment(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}
	
	public MFTUMatchPOConsignment(Properties ctx,
			int FTU_MatchPOConsignment_ID, String trxName) {
		super(ctx, FTU_MatchPOConsignment_ID, trxName);
		if(FTU_MatchPOConsignment_ID == 0)
		{
			setDateTrx(new Timestamp(System.currentTimeMillis()));
			setIsActive(true);
			setIsGenerated(false);
			setProcessed(false);
		}
	}

	@Override
	protected boolean afterSave(boolean newRecord, boolean success) {
		boolean sucessful = true;
		
		if(getC_InvoiceLine_ID()>0)
		{
			MInvoiceLine il = (MInvoiceLine) getC_InvoiceLine();
			il.set_ValueOfColumn("IsGenerated", true);
			il.saveEx(get_TrxName());
		}
		
		if(getM_InventoryLine_ID()>0)
		{
			MInventoryLine il = (MInventoryLine) getM_InventoryLine();
			il.set_ValueOfColumn("IsGenerated", true);
			il.saveEx(get_TrxName());
		}
		
		return sucessful;
	}

	@Override
	protected boolean beforeDelete() {
		boolean sucessful = true;
		if(getC_InvoiceLine_ID()>0)
		{
			MInvoiceLine il = (MInvoiceLine) getC_InvoiceLine();
			il.set_ValueOfColumn("IsGenerated", false);
			il.saveEx(get_TrxName());
		}
		
		if(getM_InventoryLine_ID()>0)
		{
			MInventoryLine il = (MInventoryLine) getM_InventoryLine();
			il.set_ValueOfColumn("IsGenerated", false);
			il.saveEx(get_TrxName());
		}
		return sucessful;
	}
	
	

}
