/**
 * 
 */
package net.frontuari.process;

import java.util.ArrayList;
import java.util.List;

import net.frontuari.base.FTUProcess;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_AD_Org;
import org.compiere.model.I_M_Locator;
import org.compiere.model.I_M_Warehouse;
import org.compiere.model.MBPartner;
import org.compiere.model.MLocator;
import org.compiere.model.MOrg;
import org.compiere.model.MWarehouse;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.Env;
import org.compiere.util.Msg;

/**
 * @author dixon
 *
 */
public class CreateBusinessLocator extends FTUProcess {

	private int p_C_BPartner_ID = -1;
	
	private int p_AD_Org_ID = -1;
	
	private boolean p_IsAccessAllOrgs = false;
	
	/* (non-Javadoc)
	 * @see org.compiere.process.SvrProcess#prepare()
	 */
	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (ProcessInfoParameter parameter : para) {
			String name = parameter.getParameterName();
			if (name != null) {
				if (name.equals("C_BPartner_ID"))
					p_C_BPartner_ID = parameter.getParameterAsInt();
				else if (name.equals("AD_Org_ID"))
					p_AD_Org_ID = parameter.getParameterAsInt();
				if (name.equals("IsAccessAllOrgs"))
					p_IsAccessAllOrgs = parameter.getParameterAsBoolean();
			}
		}
		if(p_C_BPartner_ID <= 0
				|| getRecord_ID() > 0) 
			p_C_BPartner_ID = getRecord_ID();
	}

	/* (non-Javadoc)
	 * @see org.compiere.process.SvrProcess#doIt()
	 */
	@Override
	protected String doIt() throws Exception {
		if(p_C_BPartner_ID <= 0)
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@");
		if(p_AD_Org_ID <= 0
				&& !p_IsAccessAllOrgs)
			throw new AdempiereException("@AD_Org_ID@ @NotFound@");
		
		MBPartner partner = MBPartner.get(getCtx(), p_C_BPartner_ID);
		
		List<Object> parameters = new ArrayList<>();
		String whereClause = "IsConsignmentOrg = ? ";
		parameters.add(true);
		if(p_AD_Org_ID > 0) {
			parameters.add(p_AD_Org_ID);
			whereClause += " AND AD_Org_ID = ?";
		}
		
		//	Search Organizations is
		List<MOrg> orgLists = new Query(Env.getCtx(), I_AD_Org.Table_Name, whereClause, get_TrxName())
			.setParameters(parameters)
			.setOnlyActiveRecords(true)
			.setClient_ID()
			.list();
		
		for (MOrg mOrg : orgLists) {
			if(mOrg.getInfo().getC_Location_ID() <= 0) {
				throw new AdempiereException("@C_Location_ID@ @NotFound@ - @AD_Org_ID@ " + mOrg.getName());
			}
			parameters = new ArrayList<>();
			parameters.add(true);
			parameters.add(mOrg.getAD_Org_ID());
			whereClause = "IsConsignmentWarehouse = ? AND AD_Org_ID = ?";
			List<MWarehouse> wrhList = new Query(Env.getCtx(), I_M_Warehouse.Table_Name, whereClause, get_TrxName())
				.setParameters(parameters)
				.setOnlyActiveRecords(true)
				.setClient_ID()
				.list();
			if(wrhList.isEmpty()) {
				MWarehouse wrh = new MWarehouse(mOrg);
				wrh.setName(wrh.getName() + " - " + Msg.translate(getCtx(), "IsConsignmentWarehouse"));
				wrh.set_ValueOfColumn("IsConsignmentWarehouse", true);
				wrh.saveEx();
				addLog("@M_Warehouse_ID@ @of@ @AD_Org_ID@ " + mOrg.getName() + "\n" + wrh.getName());
				wrhList.add(wrh);
			}
			for (MWarehouse mWarehouse : wrhList) {
				parameters = new ArrayList<>();
				whereClause = "M_Warehouse_ID = ? AND C_BPartner_ID = ?";
				parameters.add(mWarehouse.getM_Warehouse_ID());
				parameters.add(p_C_BPartner_ID);
				List<MLocator> lctList = new Query(Env.getCtx(), I_M_Locator.Table_Name, whereClause, get_TrxName())
					.setParameters(parameters)
					.setOnlyActiveRecords(true)
					.setClient_ID()
					.list();
				if(lctList.isEmpty()) {
					String value = partner.getTaxID();
					if(value == null) {
						value = partner.getValue();
					}
					MLocator loct = new MLocator(mWarehouse, value);
					loct.set_ValueOfColumn("C_BPartner_ID", p_C_BPartner_ID);
					loct.saveEx();
					addLog("@M_Locator_ID@ @of@ @C_BPartner_ID@  " + partner.getName()  + "\n" + loct.getValue() );
				}
			}
		}
		
		return "";
	}

}
