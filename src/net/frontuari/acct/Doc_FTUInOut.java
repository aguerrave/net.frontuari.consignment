package net.frontuari.acct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Level;

import org.compiere.acct.Doc;
import org.compiere.acct.DocLine;
import org.compiere.acct.Doc_InOut;
import org.compiere.acct.Fact;
import org.compiere.acct.FactLine;
import org.compiere.model.I_M_InOutLine;
import org.compiere.model.I_M_RMALine;
import org.compiere.model.MAccount;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MCostDetail;
import org.compiere.model.MCurrency;
import org.compiere.model.MInOut;
import org.compiere.model.MInOutLine;
import org.compiere.model.MOrderLandedCostAllocation;
import org.compiere.model.MOrderLine;
import org.compiere.model.MProduct;
import org.compiere.model.MTax;
import org.compiere.model.ProductCost;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Util;

public class Doc_FTUInOut extends Doc_InOut {

	public Doc_FTUInOut(MAcctSchema as, ResultSet rs, String trxName) {
		super(as, rs, trxName);
	}
	
	private int				m_Reversal_ID = 0;
	
	/**
	 *  Create Facts (the accounting logic) for
	 *  MMS, MMR.
	 *  <pre>
	 *  Shipment
	 *      CoGS (RevOrg)   DR
	 *      Inventory               CR
	 *  Shipment of Project Issue
	 *      CoGS            DR
	 *      Project                 CR
	 *  Receipt
	 *      Inventory       DR
	 *      NotInvoicedReceipt      CR
	 *  </pre>
	 *  @param as accounting schema
	 *  @return Fact
	 */
	@Override
	public ArrayList<Fact> createFacts (MAcctSchema as)
	{
		//
		ArrayList<Fact> facts = new ArrayList<Fact>();
		//  create Fact Header
		Fact fact = new Fact(this, as, Fact.POST_Actual);
		setC_Currency_ID (as.getC_Currency_ID());

		//  Line pointers
		FactLine dr = null;
		FactLine cr = null;

		//  *** Sales - Shipment
		if (getDocumentType().equals(DOCTYPE_MatShipment) && isSOTrx())
		{
			for (int i = 0; i < p_lines.length; i++)
			{
				DocLine line = p_lines[i];				
				MProduct product = line.getProduct();
				BigDecimal costs = null;
				if (!isReversal(line))
				{
					// MZ Goodwill
					// if Shipment CostDetail exist then get Cost from Cost Detail
					costs = line.getProductCosts(as, line.getAD_Org_ID(), true, "M_InOutLine_ID=?");				
					// end MZ
					if (costs == null || costs.signum() == 0)	//	zero costs OK
					{
						if(product.get_ValueAsBoolean("IsConsignmentProduct"))
						{
							String sql = "SELECT PO_PriceList_ID "
									+ "FROM FTU_RV_Consignment_Storage cs "
									+ "JOIN C_BPartner bp ON cs.C_BPartner_ID = bp.C_BPartner_ID "
									+ "WHERE cs.M_Product_ID = ?";
							int pl_ID = DB.getSQLValue(getTrxName(), sql, product.get_ID());
							if(pl_ID > 0)
							{
								costs = DB.getSQLValueBD(getTrxName(), 
										"SELECT PriceList FROM M_ProductPrice pp JOIN M_PriceList_Version plv ON pp.M_PriceList_Version_ID = plv.M_PriceList_Version_ID WHERE plv.M_PriceList_ID = ? AND pp.M_Product_ID = ? AND plv.ValidFrom <= ? ORDER BY plv.ValidFrom DESC", 
										new Object[]{pl_ID,product.get_ID(),getDateDoc()});
							}
							else
								costs = BigDecimal.ZERO;
						}
						else if (product.isStocked())
						{
							//ok if we have purchased zero cost item from vendor before
							int count = DB.getSQLValue(null, "SELECT Count(*) FROM M_CostDetail WHERE M_Product_ID=? AND Processed='Y' AND Amt=0.00 AND Qty > 0 AND (C_OrderLine_ID > 0 OR C_InvoiceLine_ID > 0)", 
									product.getM_Product_ID());
							if (count > 0)
							{
								costs = BigDecimal.ZERO;
							}
							else
							{
								p_Error = "No Costs for " + line.getProduct().getName();
								log.log(Level.WARNING, p_Error);
								return null;
							}
						}
						else	//	ignore service
							continue;
					}
				}
				else
				{
					//temp to avoid NPE
					costs = BigDecimal.ZERO;
				}
				
				//  CoGS            DR
				dr = fact.createLine(line,
					line.getAccount(ProductCost.ACCTTYPE_P_Cogs, as),
					as.getC_Currency_ID(), costs, null);
				if (dr == null)
				{
					p_Error = "FactLine DR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				dr.setM_Locator_ID(line.getM_Locator_ID());
				dr.setLocationFromLocator(line.getM_Locator_ID(), true);    //  from Loc
				dr.setLocationFromBPartner(getC_BPartner_Location_ID(), false);  //  to Loc
				dr.setAD_Org_ID(line.getOrder_Org_ID());		//	Revenue X-Org
				dr.setQty(line.getQty().negate());
				
				if (isReversal(line))
				{
					//	Set AmtAcctDr from Original Shipment/Receipt
					if (!dr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						if (! product.isStocked())	{ //	ignore service
							fact.remove(dr);
							continue;
						}
						p_Error = "Original Shipment/Receipt not posted yet";
						return null;
					}
				}

				//  Inventory               CR
				cr = fact.createLine(line,
					line.getAccount(ProductCost.ACCTTYPE_P_Asset, as),
					as.getC_Currency_ID(), null, costs);
				if (cr == null)
				{
					p_Error = "FactLine CR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				cr.setM_Locator_ID(line.getM_Locator_ID());
				cr.setLocationFromLocator(line.getM_Locator_ID(), true);    // from Loc
				cr.setLocationFromBPartner(getC_BPartner_Location_ID(), false);  // to Loc
				
				if (isReversal(line))
				{
					//	Set AmtAcctCr from Original Shipment/Receipt
					if (!cr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						p_Error = "Original Shipment/Receipt not posted yet";
						return null;
					}
					costs = cr.getAcctBalance(); //get original cost
				}
				//
				if (line.getM_Product_ID() != 0)
				{
					if (!MCostDetail.createShipment(as, line.getAD_Org_ID(),
						line.getM_Product_ID(), line.getM_AttributeSetInstance_ID(),
						line.get_ID(), 0,
						costs, line.getQty(),
						line.getDescription(), true, getTrxName()))
					{
						p_Error = "Failed to create cost detail record";
						return null;
					}
				}
			}	//	for all lines

			/** Commitment release										****/
			/*if (as.isAccrual() && as.isCreateSOCommitment())
			{
				for (int i = 0; i < p_lines.length; i++)
				{
					DocLine line = p_lines[i];
					Fact factcomm = Doc_Order.getCommitmentSalesRelease(as, this,
						line.getQty(), line.get_ID(), Env.ONE);
					if (factcomm != null)
						facts.add(factcomm);
				}
			}	//	Commitment
			*/

		}	//	Shipment
        //	  *** Sales - Return
		else if ( getDocumentType().equals(DOCTYPE_MatReceipt) && isSOTrx() )
		{
			for (int i = 0; i < p_lines.length; i++)
			{
				DocLine line = p_lines[i];
				MProduct product = line.getProduct();
				BigDecimal costs = null;
				if (!isReversal(line)) 
				{
					// MZ Goodwill
					// if Shipment CostDetail exist then get Cost from Cost Detail
					costs = line.getProductCosts(as, line.getAD_Org_ID(), true, "M_InOutLine_ID=?");
					// end MZ
	
					if (costs == null || costs.signum() == 0)	//	zero costs OK
					{
						if (product.isStocked())
						{
							p_Error = "No Costs for " + line.getProduct().getName();
							log.log(Level.WARNING, p_Error);
							return null;
						}
						else	//	ignore service
							continue;
					}
				} 
				else
				{
					costs = BigDecimal.ZERO;
				}
				//  Inventory               DR
				dr = fact.createLine(line,
					line.getAccount(ProductCost.ACCTTYPE_P_Asset, as),
					as.getC_Currency_ID(), costs, null);
				if (dr == null)
				{
					p_Error = "FactLine DR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				dr.setM_Locator_ID(line.getM_Locator_ID());
				dr.setLocationFromLocator(line.getM_Locator_ID(), true);    // from Loc
				dr.setLocationFromBPartner(getC_BPartner_Location_ID(), false);  // to Loc
				if (isReversal(line))
				{
					//	Set AmtAcctDr from Original Shipment/Receipt
					if (!dr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						if (! product.isStocked())	{ //	ignore service
							fact.remove(dr);
							continue;
						}
						p_Error = "Original Shipment/Receipt not posted yet";
						return null;
					}
					costs = dr.getAcctBalance(); //get original cost
				}
				//
				if (line.getM_Product_ID() != 0)
				{
					if (!MCostDetail.createShipment(as, line.getAD_Org_ID(),
						line.getM_Product_ID(), line.getM_AttributeSetInstance_ID(),
						line.get_ID(), 0,
						costs, line.getQty(),
						line.getDescription(), true, getTrxName()))
					{
						p_Error = "Failed to create cost detail record";
						return null;
					}
				}

				//  CoGS            CR
				cr = fact.createLine(line,
					line.getAccount(ProductCost.ACCTTYPE_P_Cogs, as),
					as.getC_Currency_ID(), null, costs);
				if (cr == null)
				{
					p_Error = "FactLine CR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				cr.setM_Locator_ID(line.getM_Locator_ID());
				cr.setLocationFromLocator(line.getM_Locator_ID(), true);    //  from Loc
				cr.setLocationFromBPartner(getC_BPartner_Location_ID(), false);  //  to Loc
				cr.setAD_Org_ID(line.getOrder_Org_ID());		//	Revenue X-Org
				cr.setQty(line.getQty().negate());
				if (isReversal(line))
				{
					//	Set AmtAcctCr from Original Shipment/Receipt
					if (!cr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						p_Error = "Original Shipment/Receipt not posted yet";
						return null;
					}
				}
			}	//	for all lines
		}	//	Sales Return

		//  *** Purchasing - Receipt
		else if (getDocumentType().equals(DOCTYPE_MatReceipt) && !isSOTrx())
		{
			for (int i = 0; i < p_lines.length; i++)
			{
				// Elaine 2008/06/26
				int C_Currency_ID = as.getC_Currency_ID();
				//
				DocLine line = p_lines[i];
				BigDecimal costs = null;
				MProduct product = line.getProduct();
				MOrderLine orderLine = null;
				BigDecimal landedCost = BigDecimal.ZERO;
				String costingMethod = product.getCostingMethod(as);
				if (!isReversal(line))
				{					
					int C_OrderLine_ID = line.getC_OrderLine_ID();
					if (C_OrderLine_ID > 0)
					{
						orderLine = new MOrderLine (getCtx(), C_OrderLine_ID, getTrxName());
						MOrderLandedCostAllocation[] allocations = MOrderLandedCostAllocation.getOfOrderLine(C_OrderLine_ID, getTrxName());
						for(MOrderLandedCostAllocation allocation : allocations) 
						{														
							BigDecimal totalAmt = allocation.getAmt();
							BigDecimal totalQty = allocation.getQty();
							BigDecimal amt = totalAmt.multiply(line.getQty()).divide(totalQty, RoundingMode.HALF_UP);
							landedCost = landedCost.add(amt);							
						}
					}
															
					//get costing method for product					
					if (MAcctSchema.COSTINGMETHOD_AveragePO.equals(costingMethod) ||
						MAcctSchema.COSTINGMETHOD_AverageInvoice.equals(costingMethod) ||
						MAcctSchema.COSTINGMETHOD_LastPOPrice.equals(costingMethod) )
					{
						// Low - check if c_orderline_id is valid
						if (orderLine != null)
						{
						    // Elaine 2008/06/26
						    C_Currency_ID = orderLine.getC_Currency_ID();
						    //
						    costs = orderLine.getPriceCost();
						    if (costs == null || costs.signum() == 0)
						    {
						    	costs = orderLine.getPriceActual();
								//	Goodwill: Correct included Tax
						    	int C_Tax_ID = orderLine.getC_Tax_ID();
								if (orderLine.isTaxIncluded() && C_Tax_ID != 0)
								{
									MTax tax = MTax.get(getCtx(), C_Tax_ID);
									if (!tax.isZeroTax())
									{
										int stdPrecision = MCurrency.getStdPrecision(getCtx(), C_Currency_ID);
										BigDecimal costTax = tax.calculateTax(costs, true, stdPrecision);
										if (log.isLoggable(Level.FINE)) log.fine("Costs=" + costs + " - Tax=" + costTax);
										costs = costs.subtract(costTax);
									}
								}	//	correct included Tax
						    }
						    costs = costs.multiply(line.getQty());
	                    }
	                    else
	                    {	                    	
	                    	p_Error = "Resubmit - No Costs for " + product.getName() + " (required order line)";
	                        log.log(Level.WARNING, p_Error);
	                        return null;
	                    }
	                    //
					}
					else
					{
						costs = line.getProductCosts(as, line.getAD_Org_ID(), false);	//	current costs
					}
					
					if (costs == null || costs.signum() == 0)
					{
						//ok if purchase price is actually zero 
						if (orderLine != null && orderLine.getPriceActual().signum() == 0)
                    	{
							costs = BigDecimal.ZERO;
                    	}
						else
						{
							p_Error = "Resubmit - No Costs for " + product.getName();
							log.log(Level.WARNING, p_Error);
							return null;
						}
					}										
				} 
				else
				{
					costs = BigDecimal.ZERO;
				}
				
				//  Inventory/Asset			DR
				MAccount assets = line.getAccount(ProductCost.ACCTTYPE_P_Asset, as);
				if (product.isService())
				{
					//if the line is a Outside Processing then DR WIP
					if(line.getPP_Cost_Collector_ID() > 0)
						assets = line.getAccount(ProductCost.ACCTTYPE_P_WorkInProcess, as);
					else
						assets = line.getAccount(ProductCost.ACCTTYPE_P_Expense, as);

				}

				BigDecimal drAsset = costs;
				if (landedCost.signum() != 0 && (MAcctSchema.COSTINGMETHOD_AverageInvoice.equals(costingMethod)
					|| MAcctSchema.COSTINGMETHOD_AveragePO.equals(costingMethod)))
				{
					drAsset = drAsset.add(landedCost);
				}
				dr = fact.createLine(line, assets,
					C_Currency_ID, drAsset, null);
				//
				if (dr == null)
				{
					p_Error = "DR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				dr.setM_Locator_ID(line.getM_Locator_ID());
				dr.setLocationFromBPartner(getC_BPartner_Location_ID(), true);   // from Loc
				dr.setLocationFromLocator(line.getM_Locator_ID(), false);   // to Loc
				if (isReversal(line))
				{
					//	Set AmtAcctDr from Original Shipment/Receipt
					if (!dr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						if (! product.isStocked())	{ //	ignore service
							fact.remove(dr);
							continue;
						}
						p_Error = "Original Receipt not posted yet";
						return null;
					}
				}

				//  NotInvoicedReceipt				CR
				cr = fact.createLine(line,
					getAccount(Doc.ACCTTYPE_NotInvoicedReceipts, as),
					C_Currency_ID, null, costs);
				//
				if (cr == null)
				{
					p_Error = "CR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				cr.setM_Locator_ID(line.getM_Locator_ID());
				cr.setLocationFromBPartner(getC_BPartner_Location_ID(), true);   //  from Loc
				cr.setLocationFromLocator(line.getM_Locator_ID(), false);   //  to Loc
				cr.setQty(line.getQty().negate());
				if (isReversal(line))
				{
					//	Set AmtAcctCr from Original Shipment/Receipt
					if (!cr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						p_Error = "Original Receipt not posted yet";
						return null;
					}
				}
				if (!fact.isAcctBalanced())
				{
					if (isReversal(line))
					{
						dr = fact.createLine(line,
								line.getAccount(ProductCost.ACCTTYPE_P_LandedCostClearing, as),
								C_Currency_ID, Env.ONE, (BigDecimal)null);
						if (!dr.updateReverseLine (MInOut.Table_ID,
								m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
						{
							p_Error = "Original Receipt not posted yet";
							return null;
						}
					}
					else if (landedCost.signum() != 0)
					{
						cr = fact.createLine(line,
								line.getAccount(ProductCost.ACCTTYPE_P_LandedCostClearing, as),
								C_Currency_ID, null, landedCost);
						//
						if (cr == null)
						{
							p_Error = "CR not created: " + line;
							log.log(Level.WARNING, p_Error);
							return null;
						}
						cr.setM_Locator_ID(line.getM_Locator_ID());
						cr.setLocationFromBPartner(getC_BPartner_Location_ID(), true);   //  from Loc
						cr.setLocationFromLocator(line.getM_Locator_ID(), false);   //  to Loc
						cr.setQty(line.getQty().negate());
					}
				}
			}
		}	//	Receipt
         //	  *** Purchasing - return
		else if (getDocumentType().equals(DOCTYPE_MatShipment) && !isSOTrx())
		{
			for (int i = 0; i < p_lines.length; i++)
			{
				// Elaine 2008/06/26
				int C_Currency_ID = as.getC_Currency_ID();
				//
				DocLine line = p_lines[i];
				BigDecimal costs = null;
				MProduct product = line.getProduct();
				if (!isReversal(line))
				{
					MInOutLine ioLine = (MInOutLine) line.getPO();
					I_M_RMALine rmaLine = ioLine.getM_RMALine();
					costs = rmaLine != null ? rmaLine.getAmt() : BigDecimal.ZERO;
					I_M_InOutLine originalInOutLine = rmaLine != null ? rmaLine.getM_InOutLine() : null;
					if (originalInOutLine != null && originalInOutLine.getC_OrderLine_ID() > 0)
					{
						MOrderLine originalOrderLine = (MOrderLine) originalInOutLine.getC_OrderLine();
						//	Goodwill: Correct included Tax
				    	int C_Tax_ID = originalOrderLine.getC_Tax_ID();
				    	if (originalOrderLine.isTaxIncluded() && C_Tax_ID != 0)
						{
							MTax tax = MTax.get(getCtx(), C_Tax_ID);
							if (!tax.isZeroTax())
							{
								int stdPrecision = MCurrency.getStdPrecision(getCtx(), originalOrderLine.getC_Currency_ID());
								BigDecimal costTax = tax.calculateTax(costs, true, stdPrecision);
								if (log.isLoggable(Level.FINE)) log.fine("Costs=" + costs + " - Tax=" + costTax);
								costs = costs.subtract(costTax);
							}
						}	//	correct included Tax
				    	costs = costs.multiply(line.getQty());
				    	costs = costs.negate();
					}
					else
					{
						costs = line.getProductCosts(as, line.getAD_Org_ID(), false);	//	current costs
					}
					if (costs == null || costs.signum() == 0)
					{
						p_Error = "Resubmit - No Costs for " + product.getName();
						log.log(Level.WARNING, p_Error);
						return null;
					}
				}
				else
				{
					//update below
					costs = Env.ONE;
				}
				//  NotInvoicedReceipt				DR
				// Elaine 2008/06/26
				/*dr = fact.createLine(line,
					getAccount(Doc.ACCTTYPE_NotInvoicedReceipts, as),
					as.getC_Currency_ID(), costs , null);*/
				dr = fact.createLine(line,
					getAccount(Doc.ACCTTYPE_NotInvoicedReceipts, as),
					C_Currency_ID, costs , null);
				//
				if (dr == null)
				{
					p_Error = "CR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				dr.setM_Locator_ID(line.getM_Locator_ID());
				dr.setLocationFromBPartner(getC_BPartner_Location_ID(), true);   //  from Loc
				dr.setLocationFromLocator(line.getM_Locator_ID(), false);   //  to Loc
				dr.setQty(line.getQty().negate());
				if (isReversal(line))
				{
					//	Set AmtAcctDr from Original Shipment/Receipt
					if (!dr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						if (! product.isStocked())	{ //	ignore service
							fact.remove(dr);
							continue;
						}
						p_Error = "Original Receipt not posted yet";
						return null;
					}
				}

				//  Inventory/Asset			CR
				MAccount assets = line.getAccount(ProductCost.ACCTTYPE_P_Asset, as);
				if (product.isService())
					assets = line.getAccount(ProductCost.ACCTTYPE_P_Expense, as);
				// Elaine 2008/06/26
				/*cr = fact.createLine(line, assets,
					as.getC_Currency_ID(), null, costs);*/
				cr = fact.createLine(line, assets,
					C_Currency_ID, null, costs);
				//
				if (cr == null)
				{
					p_Error = "DR not created: " + line;
					log.log(Level.WARNING, p_Error);
					return null;
				}
				cr.setM_Locator_ID(line.getM_Locator_ID());
				cr.setLocationFromBPartner(getC_BPartner_Location_ID(), true);   // from Loc
				cr.setLocationFromLocator(line.getM_Locator_ID(), false);   // to Loc
				if (isReversal(line))
				{
					//	Set AmtAcctCr from Original Shipment/Receipt
					if (!cr.updateReverseLine (MInOut.Table_ID,
							m_Reversal_ID, line.getReversalLine_ID(),Env.ONE))
					{
						p_Error = "Original Receipt not posted yet";
						return null;
					}
				}
				
				String costingError = createVendorRMACostDetail(as, line, costs);
				if (!Util.isEmpty(costingError))
				{
					p_Error = costingError;
					return null;
				}
			}
						
		}	//	Purchasing Return
		else
		{
			p_Error = "DocumentType unknown: " + getDocumentType();
			log.log(Level.SEVERE, p_Error);
			return null;
		}
		//
		facts.add(fact);
		return facts;
	}   //  createFact
	
	private boolean isReversal(DocLine line) {
		return m_Reversal_ID !=0 && line.getReversalLine_ID() != 0;
	}
	
	private String createVendorRMACostDetail(MAcctSchema as, DocLine line, BigDecimal costs)
	{		
		BigDecimal tQty = line.getQty();
		BigDecimal tAmt = costs;
		if (tAmt.signum() != tQty.signum())
		{
			tAmt = tAmt.negate();
		}
		if (!MCostDetail.createShipment(as, line.getAD_Org_ID(), line.getM_Product_ID(), 
				line.getM_AttributeSetInstance_ID(), line.get_ID(), 0, tAmt, tQty, 
				line.getDescription(), false, getTrxName()))
		{
			return "SaveError";
		}
		return "";
	}
}
