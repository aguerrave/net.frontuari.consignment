/**
 * 
 */
package net.frontuari.process;

import java.util.ArrayList;
import java.util.List;

import net.frontuari.base.FTUProcess;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.MBPartner;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.Env;

/**
 * @author dixon
 *
 */
public class BussinesPartnerConsignment extends FTUProcess {

	private int p_C_BPartner_ID = -1;
	
	private int p_C_BPGroup_ID = -1;
	
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
				if (name.equals("C_BP_Group_ID"))
					p_C_BPGroup_ID = parameter.getParameterAsInt();
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.compiere.process.SvrProcess#doIt()
	 */
	@Override
	protected String doIt() throws Exception {
		if(p_C_BPartner_ID <= 0
				&& p_C_BPGroup_ID <= 0)
			throw new AdempiereException("@C_BPartner_ID@ @NotFound@ @or@ @C_BP_Group_ID@ @NotFound@");
		
		List<Object> parameters = new ArrayList<>();
		StringBuilder whereClause = new StringBuilder("IsConsignmentPartner = ? ");
		parameters.add(false);
		if(p_C_BPGroup_ID > 0) {
			whereClause.append(" AND ").append(I_C_BPartner.COLUMNNAME_C_BP_Group_ID).append("=?");
			parameters.add(p_C_BPGroup_ID);
		} 
		
		if (p_C_BPartner_ID > 0) {
			whereClause.append(" AND ").append(I_C_BPartner.COLUMNNAME_C_BPartner_ID).append("=?");
			parameters.add(p_C_BPartner_ID);
		}
		
		//	Search Partners
		List<MBPartner> partnersLists = new Query(Env.getCtx(), I_C_BPartner.Table_Name, whereClause.toString(), get_TrxName())
			.setParameters(parameters)
			.setOnlyActiveRecords(true)
			.setClient_ID()
			.list();
		int count = 0;
		for (MBPartner partner : partnersLists) {
			partner.set_ValueOfColumn("IsConsignmentPartner", true);
			if(partner.save())
				count++;
		}
		
		
		return "@C_BPartner_ID@ @Updated@ " + count;
	}

}
