package net.frontuari.model;

import java.math.BigDecimal;
import java.util.List;

import org.compiere.acct.Fact;
import org.compiere.acct.FactLine;
import org.compiere.model.FactsValidator;
import org.compiere.model.I_C_Order;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MClient;
import org.compiere.model.MDocType;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MMatchInv;
import org.compiere.model.MMatchPO;
import org.compiere.model.MMovementLine;
import org.compiere.model.MOrder;
import org.compiere.model.MOrderLine;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_M_Product_Acct;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;

public class FTUModelValidator implements ModelValidator, FactsValidator {
	
	private int AD_Client_ID = -1;
	
	@Override
	public void initialize(ModelValidationEngine engine, MClient client) {
		
		if (client !=null)
		{
			AD_Client_ID = client.getAD_Client_ID();
		}
		
		engine.addDocValidate(MInOut.Table_Name, this);
		engine.addModelChange(MMovementLine.Table_Name, this);
		engine.addDocValidate(I_C_Order.Table_Name, this);
		engine.addModelChange(MInOutLine.Table_Name, this);
		engine.addFactsValidate(MMatchInv.Table_Name, this);
	}

	@Override
	public int getAD_Client_ID() {
		
		return AD_Client_ID;
	}

	@Override
	public String login(int AD_Org_ID, int AD_Role_ID, int AD_User_ID) {
		
		return null;
	}

	@Override
	public String modelChange(PO po, int type) throws Exception {
		
		if (po instanceof MMovementLine)
			return validateMMovementLine((MMovementLine) po);
		else if (po instanceof MInOutLine && (type == ModelValidator.TYPE_BEFORE_NEW || type == ModelValidator.TYPE_BEFORE_CHANGE))
			return validateMInOutLine((MInOutLine) po);
		
		return null;
	}
	
	private String validateMInOutLine(MInOutLine line) {
		
		MInOut mInOut = line.getParent();
		boolean isConsigmentDocument = ((MDocType) mInOut.getC_DocType()).get_ValueAsBoolean("IsConsignmentDocument");
		
		if (!isConsigmentDocument || !line.get_ValueAsBoolean("IsConfirmed"))
			return null;

		BigDecimal qtyReceipt = (BigDecimal) line.get_Value("QtyReceipt");
		BigDecimal qtyEntered = line.getQtyEntered();
		
		if (qtyEntered.compareTo(qtyReceipt) == 1)
			return Msg.getMsg(Env.getCtx(), "Qty.Invalid", new Object[] { qtyEntered, qtyReceipt });
		
		return null;
	}

	@Override
	public String docValidate(PO po, int timing) {
		
		if (po instanceof MInOut && timing == ModelValidator.TIMING_BEFORE_COMPLETE)
			return validateMInOut((MInOut) po);
		
		if (po instanceof MOrder) {
			if(timing == ModelValidator.TIMING_BEFORE_REVERSECORRECT
					|| timing == ModelValidator.TIMING_BEFORE_VOID) {
				MOrder order = (MOrder) po;
				
				for (MOrderLine line : order.getLines(" AND EXISTS (SELECT 1 FROM FTU_MatchPOConsignment mpoc WHERE mpoc.C_OrderLine_ID = C_OrderLine.C_OrderLine_ID)", "")) {
					MMatchPO[] mPo = MMatchPO.getOrderLine(po.getCtx(), line.getC_OrderLine_ID(), po.get_TrxName());
					for (MMatchPO mMatchPO : mPo) {
						int movementLineID = DB.getSQLValue(po.get_TrxName(), "SELECT M_MovementLine_ID FROM M_MovementLine WHERE M_InOutLine_ID = ? ", mMatchPO.getM_InOutLine_ID());
						if(movementLineID > 0 ) {
							MMovementLine movementLine = new MMovementLine(po.getCtx(), movementLineID, po.get_TrxName());
							if(movementLine != null) {
								BigDecimal qty = ((BigDecimal) movementLine.get_Value("QtyInvoiced")).subtract(mMatchPO.getQty());
								movementLine.set_ValueOfColumn("QtyInvoiced", qty);
								movementLine.saveEx(po.get_TrxName());
							}
						}
						mMatchPO.setQty(BigDecimal.ZERO);
						mMatchPO.saveEx(po.get_TrxName());
					}
					//	Unconfirm C_InvoiceLine
					DB.executeUpdate("UPDATE C_InvoiceLine il set IsGenerated ='N' "
							+ " FROM (SELECT C_InvoiceLine_ID FROM FTU_MatchPOConsignment fm "
							+ " WHERE fm.C_OrderLine_ID = "+line.get_ID()+") mpoc "
									+ "WHERE il.C_InvoiceLine_ID = mpoc.C_InvoiceLine_ID",po.get_TrxName());
					//	Unconfirm M_InventoryLine
					DB.executeUpdate("UPDATE M_InventoryLine il set IsGenerated ='N' "
							+ " FROM (SELECT M_InventoryLine_ID FROM FTU_MatchPOConsignment fm "
							+ " WHERE fm.C_OrderLine_ID = "+line.get_ID()+") mpoc "
									+ "WHERE il.M_InventoryLine_ID = mpoc.M_InventoryLine_ID",po.get_TrxName());
					//	Delete MatchPOConsignment
					DB.executeUpdate("DELETE FROM FTU_MatchPOConsignment WHERE C_OrderLine_ID = "+line.get_ID(),po.get_TrxName());
				}
			}
		}
		
		
		return null;
	}
	
	private int getQtyUnconfirmed(MInOut inOut) {
		
		StringBuffer sql = new StringBuffer("SELECT COUNT(*) FROM M_InOutLine iol")
							.append(" WHERE iol.M_InOut_ID = ? AND iol.IsACtive = 'Y' AND iol.IsConfirmed = 'N'");
		
		int qty = DB.getSQLValue(inOut.get_TrxName(), sql.toString(), inOut.get_ID());
		return qty;
	}
	
	private String validateMInOut(MInOut inOut) {
		
		boolean isConsignmentDocument = ((MDocType) inOut.getC_DocType()).get_ValueAsBoolean("IsConsignmentDocument");
		
		if (!isConsignmentDocument)
			return null;
		
		int qty = getQtyUnconfirmed(inOut);
		
		if (qty != 0)
			return "@Not.All.Selected.Lines@";
		
		return null;
	}
	
	private String validateMMovementLine(MMovementLine line) {
		
		int M_InOutLine_ID = line.get_ValueAsInt("M_InOutLine_ID");
		
		if (M_InOutLine_ID == 0)
			return null;
		if( line.is_ValueChanged("M_InOutLine_ID") 
								|| line.is_ValueChanged("MovementQty") ){
			if (exceedsRelatedAmount(line))
					return "@Line.Exceeds.Amount@: ["+line.getLine()+": "+line.getM_Product().getName()+" @MovementQty@: "+line.getMovementQty()+"]";
		}
		return null;
	}
	
	private boolean exceedsRelatedAmount(MMovementLine line) {
		
		StringBuffer sql = new StringBuffer("SELECT iol.AvailableQty FROM FTU_RV_RelatedMovement iol")
							.append(" WHERE iol.M_InOutLine_ID = ? ");
		
		BigDecimal qty = DB.getSQLValueBD(null, sql.toString(), line.get_ValueAsInt("M_InOutLine_ID"));
		
		if(qty == null)
			qty = BigDecimal.ZERO;
		
		return line.getMovementQty().compareTo(qty) == 1;
	}

	@Override
	public String factsValidate(MAcctSchema schema, List<Fact> facts, PO po) {
		
		if (po instanceof MMatchInv)
			return validateMatchInv(schema, facts, (MMatchInv) po);
		
		return null;
	}
	
	private String validateMatchInv(MAcctSchema schema, List<Fact> facts, MMatchInv mInv) {
		
		if (mInv.getM_InOutLine_ID() == 0)
			return null;
		
		MInOutLine ioLine = new MInOutLine(mInv.getCtx(), mInv.getM_InOutLine_ID(), mInv.get_TrxName());
		MInOut inOut = ioLine.getParent();
		MDocType docType = new MDocType(mInv.getCtx(), inOut.getC_DocType_ID(), mInv.get_TrxName());
		boolean isConsignmentDocument = docType.get_ValueAsBoolean("IsConsignmentDocument");
		
		if (!isConsignmentDocument)
			return null;
		
		int m_Product_ID = mInv.getM_Product_ID();
		X_M_Product_Acct productAcct = new Query(mInv.getCtx(), X_M_Product_Acct.Table_Name,
							"M_Product_ID = ? AND C_AcctSchema_ID = ?", mInv.get_TrxName())
								.setParameters(m_Product_ID, schema.get_ID())
								.setOnlyActiveRecords(true)
								.first();
		
		int p_Asset_Acct = productAcct.getP_Asset_Acct();
		int p_COGS_Acct = productAcct.getP_COGS_Acct();
		
		MAccount cogsAcct = new MAccount(mInv.getCtx(), p_COGS_Acct, mInv.get_TrxName());
		MAccount assetAcct = new MAccount(mInv.getCtx(), p_Asset_Acct, mInv.get_TrxName());
		FactLine line = getLineByAccount(schema, facts, assetAcct.getAccount_ID());
		
		if(line != null)
			line.setAccount(schema, cogsAcct);
		
		return null;
	}
	
	private FactLine getLineByAccount(MAcctSchema schema, List<Fact> facts, int accountId) {
		
		for (Fact fact: facts) 
			if (schema.get_ID() == fact.getAcctSchema().get_ID()) // We verify that we are in the scheme in which we are accounting
				for (FactLine line: fact.getLines())
					if (line.getAccount_ID() == accountId) // Then we return the line that has the desired account
						return line;
		
		return null;
	}

}
