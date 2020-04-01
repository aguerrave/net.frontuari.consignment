/**
 * 
 */
package net.frontuari.model;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.MBPGroup;
import org.compiere.model.MDocType;
import org.compiere.model.MInOutLine;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MMatchInv;
import org.compiere.model.MMatchPO;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.ValueNamePair;

/**
 * @author fcarrillo
 *
 */
public class FTUMatchPO extends MMatchPO {

	/**
	 * 
	 */
	private static final long serialVersionUID = -1909066591860777265L;
	
	/**	Static Logger	*/
	private static CLogger	s_log	= CLogger.getCLogger (FTUMatchPO.class);
	
	/**
	 * @param ctx
	 * @param M_MatchPO_ID
	 * @param trxName
	 */
	public FTUMatchPO(Properties ctx, int M_MatchPO_ID, String trxName) {
		super(ctx, M_MatchPO_ID, trxName);
	}

	/**
	 * @param ctx
	 * @param rs
	 * @param trxName
	 */
	public FTUMatchPO(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
	}

	/**
	 * @param sLine
	 * @param dateTrx
	 * @param qty
	 */
	public FTUMatchPO(MInOutLine sLine, Timestamp dateTrx, BigDecimal qty) {
		super(sLine, dateTrx, qty);
	}

	/**
	 * @param iLine
	 * @param dateTrx
	 * @param qty
	 */
	public FTUMatchPO(MInvoiceLine iLine, Timestamp dateTrx, BigDecimal qty) {
		super(iLine, dateTrx, qty);
	}
	
	/**
	 * 	Get PO Match of Receipt Line
	 *	@param ctx context
	 *	@param M_InOutLine_ID receipt
	 *	@param trxName transaction
	 *	@return array of matches
	 */
	public static FTUMatchPO[] get (Properties ctx,
		int M_InOutLine_ID, String trxName)
	{
		if (M_InOutLine_ID == 0)
			return new FTUMatchPO[]{};
		//
		String sql = "SELECT * FROM M_MatchPO WHERE M_InOutLine_ID=?";
		ArrayList<MMatchPO> list = new ArrayList<MMatchPO>();
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);
			pstmt.setInt (1, M_InOutLine_ID);
			rs = pstmt.executeQuery ();
			while (rs.next ())
				list.add (new FTUMatchPO (ctx, rs, trxName));
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, sql, e);
			if (e instanceof RuntimeException)
			{
				throw (RuntimeException)e;
			}
			else
			{
				throw new IllegalStateException(e);
			}
		}
		finally 
		{
			DB.close(rs, pstmt);
		}
		
		FTUMatchPO[] retValue = new FTUMatchPO[list.size()];
		list.toArray (retValue);
		return retValue;
	}	//	get
	
	/**
	 * 	Get PO Matches for OrderLine
	 *	@param ctx context
	 *	@param C_OrderLine_ID order
	 *	@param trxName transaction
	 *	@return array of matches
	 */
	public static FTUMatchPO[] getOrderLine (Properties ctx, int C_OrderLine_ID, String trxName)
	{
		if (C_OrderLine_ID == 0)
			return new FTUMatchPO[]{};
		//
		String sql = "SELECT * FROM M_MatchPO WHERE C_OrderLine_ID=?";
		ArrayList<FTUMatchPO> list = new ArrayList<FTUMatchPO>();
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);
			pstmt.setInt (1, C_OrderLine_ID);
			rs = pstmt.executeQuery ();
			while (rs.next ())
				list.add (new FTUMatchPO (ctx, rs, trxName));
		}
		catch (Exception e)
		{
			s_log.log(Level.SEVERE, sql, e); 
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		FTUMatchPO[] retValue = new FTUMatchPO[list.size()];
		list.toArray (retValue);
		return retValue;
	}	//	getOrderLine
	
	/**
	 * Override beforeSave for prevent that set C_InvoiceLine_ID
	 */
	@Override
	protected boolean beforeSave (boolean newRecord)
	{
		//	Set Trx Date
		if (getDateTrx() == null)
			setDateTrx (new Timestamp(System.currentTimeMillis()));
		//	Set Acct Date
		if (getDateAcct() == null)
		{
			Timestamp ts = getNewerDateAcct();
			if (ts == null)
				ts = getDateTrx();
			setDateAcct (ts);
		}
		//	Set ASI from Receipt
		if (getM_AttributeSetInstance_ID() == 0 && getM_InOutLine_ID() != 0)
		{
			MInOutLine iol = new MInOutLine (getCtx(), getM_InOutLine_ID(), get_TrxName());
			setM_AttributeSetInstance_ID(iol.getM_AttributeSetInstance_ID());
		}
		
		// Bayu, Sistematika
		// BF [ 2240484 ] Re MatchingPO, MMatchPO doesn't contains Invoice info
		// If newRecord, set c_invoiceline_id while null
		
		//	Jorge Colmenarez, jlct.master@gmail.com, jcolmenarez@frontuari.net, 2020-01-24 10:40
		//	Support for not set C_InvoiceLine_ID when Doc Trx its Consignment
		MDocType doct = ((MDocType) getC_OrderLine().getC_Order().getC_DocTypeTarget());
		if (newRecord && getC_InvoiceLine_ID() == 0 && !doct.get_ValueAsBoolean("IsProformaOrder")) 
		{
			MMatchInv[] mpi = MMatchInv.getInOutLine(getCtx(), getM_InOutLine_ID(), get_TrxName());
			for (int i = 0; i < mpi.length; i++) 
			{
				if (mpi[i].getC_InvoiceLine_ID() != 0 && 
						mpi[i].getM_AttributeSetInstance_ID() == getM_AttributeSetInstance_ID()) 
				{
					if (mpi[i].getQty().compareTo(getQty()) == 0)  // same quantity
					{
						setC_InvoiceLine_ID(mpi[i].getC_InvoiceLine_ID());
						break;
					}
					else // create MatchPO record for PO-Invoice if different quantity
					{
						MInvoiceLine il = new MInvoiceLine(getCtx(), mpi[i].getC_InvoiceLine_ID(), get_TrxName());						
						MMatchPO match = new MMatchPO(il, getDateTrx(), mpi[i].getQty());
						match.setC_OrderLine_ID(getC_OrderLine_ID());
						if (!match.save())
						{
							String msg = "Failed to create match po";
							ValueNamePair error = CLogger.retrieveError();
							if (error != null)
								msg = msg + " " + error.getName();
							throw new RuntimeException(msg);
						}
					}
				}
			}
		}
		// end Bayu
		
		//	Find OrderLine
		if (getC_OrderLine_ID() == 0)
		{
			MInvoiceLine il = null;
			if (getC_InvoiceLine_ID() != 0)
			{
				il = getInvoiceLine();
				if (il.getC_OrderLine_ID() != 0)
					setC_OrderLine_ID(il.getC_OrderLine_ID());
			}	//	get from invoice
			if (getC_OrderLine_ID() == 0 && getM_InOutLine_ID() != 0)
			{
				MInOutLine iol = new MInOutLine (getCtx(), getM_InOutLine_ID(), get_TrxName());
				if (iol.getC_OrderLine_ID() != 0)
				{
					setC_OrderLine_ID(iol.getC_OrderLine_ID());
					if (il != null)
					{
						il.setC_OrderLine_ID(iol.getC_OrderLine_ID());
						il.saveEx();
					}
				}
			}	//	get from shipment
		}	//	find order line
		
		//	Price Match Approval
		if (getC_OrderLine_ID() != 0 
			&& getC_InvoiceLine_ID() != 0
			&& (newRecord || 
				is_ValueChanged("C_OrderLine_ID") || is_ValueChanged("C_InvoiceLine_ID")))
		{
			BigDecimal poPrice = getOrderLine().getPriceActual();
			BigDecimal invPrice = getInvoicePriceActual();
			BigDecimal difference = poPrice.subtract(invPrice);
			if (difference.signum() != 0)
			{
				difference = difference.multiply(getQty());
				setPriceMatchDifference(difference);
				//	Approval
				MBPGroup group = MBPGroup.getOfBPartner(getCtx(), getOrderLine().getC_BPartner_ID());
				BigDecimal mt = group.getPriceMatchTolerance();
				if (mt != null && mt.signum() != 0)
				{
					BigDecimal poAmt = poPrice.multiply(getQty());
					BigDecimal maxTolerance = poAmt.multiply(mt);
					maxTolerance = maxTolerance.abs()
						.divide(Env.ONEHUNDRED, 2, BigDecimal.ROUND_HALF_UP);
					difference = difference.abs();
					boolean ok = difference.compareTo(maxTolerance) <= 0;
					if (log.isLoggable(Level.CONFIG)) log.config("Difference=" + getPriceMatchDifference() 
						+ ", Max=" + maxTolerance + " => " + ok);
					setIsApproved(ok);
				}
			}
			else
			{
				setPriceMatchDifference(difference);
				setIsApproved(true);
			}
			
			//validate against M_MatchInv
			if (getM_InOutLine_ID() > 0 && getC_InvoiceLine_ID() > 0)
			{
				int cnt = DB.getSQLValue(get_TrxName(), "SELECT Count(*) FROM M_MatchInv WHERE M_InOutLine_ID="+getM_InOutLine_ID()
						+" AND C_InvoiceLine_ID="+getC_InvoiceLine_ID());
				if (cnt <= 0)
				{
					MInvoiceLine invoiceLine = new MInvoiceLine(getCtx(), getC_InvoiceLine_ID(), get_TrxName());
					MInOutLine inoutLine = new MInOutLine(getCtx(), getM_InOutLine_ID(), get_TrxName());
					throw new IllegalStateException("[MatchPO] Missing corresponding invoice matching record for invoice line "
							+ invoiceLine + " and receipt line " + inoutLine);
				}
			}
		}
		
		return true;
	}	//	beforeSave	
	
}
