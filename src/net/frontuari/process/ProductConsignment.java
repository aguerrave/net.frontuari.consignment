/**
 * 
 */
package net.frontuari.process;

import java.util.ArrayList;
import java.util.List;

import net.frontuari.base.FTUProcess;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_M_Product;
import org.compiere.model.MProduct;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.util.Env;

/**
 * @author dixon
 *
 */
public class ProductConsignment extends FTUProcess {
	

	private int p_M_Product_ID = -1;
	
	private int p_M_Product_Category_ID = -1;
	
	private int p_C_BPartner_ID = -1;
	
	/* (non-Javadoc)
	 * @see org.compiere.process.SvrProcess#prepare()
	 */
	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (ProcessInfoParameter parameter : para) {
			String name = parameter.getParameterName();
			if (name != null) {
				if (name.equals("M_Product_ID"))
					p_M_Product_ID = parameter.getParameterAsInt();
				if (name.equals("M_Product_Category_ID"))
					p_M_Product_Category_ID = parameter.getParameterAsInt();
				if (name.equals("C_BPartner_ID"))
					p_C_BPartner_ID = parameter.getParameterAsInt();
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.compiere.process.SvrProcess#doIt()
	 */
	@Override
	protected String doIt() throws Exception {
		if(p_M_Product_ID <= 0
				&& p_M_Product_Category_ID <= 0 
				&& p_C_BPartner_ID <= 0 )
			throw new AdempiereException("@M_Product_ID@ @NotFound@ @or@ @M_Product_Category_ID@ @NotFound@ @or@ @C_BPartner_ID@ @NotFound@");
		
		List<Object> parameters = new ArrayList<>();
		StringBuilder whereClause = new StringBuilder("IsConsignmentProduct = ? ");
		parameters.add(false);
		if(p_M_Product_Category_ID > 0) {
			whereClause.append(" AND ").append(I_M_Product.COLUMNNAME_M_Product_Category_ID).append("=?");
			parameters.add(p_M_Product_Category_ID);
		} 
		
		if (p_M_Product_ID > 0) {
			whereClause.append(" AND ").append(I_M_Product.COLUMNNAME_M_Product_ID).append("=?");
			parameters.add(p_M_Product_ID);
		} 
		
		if (p_C_BPartner_ID > 0)
		{
			whereClause.append(" AND M_Product_ID IN (SELECT M_Product_ID FROM BSCA_ProductConsignment WHERE C_BPartner_ID=? AND IsActive = 'Y') ");
			parameters.add(p_C_BPartner_ID);
		}
		
		//	Search Partners
		List<MProduct> productLists = new Query(Env.getCtx(), I_M_Product.Table_Name, whereClause.toString(), get_TrxName())
			.setParameters(parameters)
			.setOnlyActiveRecords(true)
			.setClient_ID()
			.list();
		int count = 0;
		for (MProduct product : productLists) {
			product.set_ValueOfColumn("IsConsignmentProduct", true);
			if(product.save())
				count++;
		}
		
		
		return "@" + I_M_Product.COLUMNNAME_M_Product_ID + "@ @Updated@ " + count;
	}
}
